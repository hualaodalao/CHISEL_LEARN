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

void VsvsimTestbench::getBitWidthImpl_io_status_cOccupancy(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_status_cOccupancy\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_status_cOccupancy");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_status_cOccupancy_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_status_cOccupancy_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_status_cOccupancy(svBitVecVal* value_io_status_cOccupancy) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_status_cOccupancy\n"); );
    // Locals
    SData/*11:0*/ value_io_status_cOccupancy__Vcvt;
    value_io_status_cOccupancy__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_status_cOccupancy");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_status_cOccupancy_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_status_cOccupancy_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_status_cOccupancy__Vcvt);
    VL_SET_SVBV_I(12, value_io_status_cOccupancy, value_io_status_cOccupancy__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_status_bOccupancy(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_status_bOccupancy\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_status_bOccupancy");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_status_bOccupancy_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_status_bOccupancy_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_status_bOccupancy(svBitVecVal* value_io_status_bOccupancy) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_status_bOccupancy\n"); );
    // Locals
    CData/*6:0*/ value_io_status_bOccupancy__Vcvt;
    value_io_status_bOccupancy__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_status_bOccupancy");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_status_bOccupancy_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_status_bOccupancy_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_status_bOccupancy__Vcvt);
    VL_SET_SVBV_I(7, value_io_status_bOccupancy, value_io_status_bOccupancy__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_status_aOccupancy(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_status_aOccupancy\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_status_aOccupancy");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_status_aOccupancy_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_status_aOccupancy_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_status_aOccupancy(svBitVecVal* value_io_status_aOccupancy) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_status_aOccupancy\n"); );
    // Locals
    SData/*11:0*/ value_io_status_aOccupancy__Vcvt;
    value_io_status_aOccupancy__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_status_aOccupancy");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_status_aOccupancy_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_status_aOccupancy_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_status_aOccupancy__Vcvt);
    VL_SET_SVBV_I(12, value_io_status_aOccupancy, value_io_status_aOccupancy__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_status_progress(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_status_progress\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_status_progress");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_status_progress_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_status_progress_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_status_progress(svBitVecVal* value_io_status_progress) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_status_progress\n"); );
    // Locals
    SData/*15:0*/ value_io_status_progress__Vcvt;
    value_io_status_progress__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_status_progress");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_status_progress_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_status_progress_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_status_progress__Vcvt);
    VL_SET_SVBV_I(16, value_io_status_progress, value_io_status_progress__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_status_err(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_status_err\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_status_err");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_status_err_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_status_err_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_status_err(svBitVecVal* value_io_status_err) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_status_err\n"); );
    // Locals
    CData/*0:0*/ value_io_status_err__Vcvt;
    value_io_status_err__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_status_err");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_status_err_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_status_err_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_status_err__Vcvt);
    VL_SET_SVBV_I(1, value_io_status_err, value_io_status_err__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_status_done(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_status_done\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_status_done");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_status_done_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_status_done_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_status_done(svBitVecVal* value_io_status_done) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_status_done\n"); );
    // Locals
    CData/*0:0*/ value_io_status_done__Vcvt;
    value_io_status_done__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_status_done");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_status_done_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_status_done_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_status_done__Vcvt);
    VL_SET_SVBV_I(1, value_io_status_done, value_io_status_done__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_status_busy(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_status_busy\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_status_busy");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_status_busy_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_status_busy_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_status_busy(svBitVecVal* value_io_status_busy) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_status_busy\n"); );
    // Locals
    CData/*0:0*/ value_io_status_busy__Vcvt;
    value_io_status_busy__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_status_busy");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_status_busy_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_status_busy_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_status_busy__Vcvt);
    VL_SET_SVBV_I(1, value_io_status_busy, value_io_status_busy__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_dma2Ext_cmd_payload_addr(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_dma2Ext_cmd_payload_addr\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_dma2Ext_cmd_payload_addr");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_dma2Ext_cmd_payload_addr_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_dma2Ext_cmd_payload_addr_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_dma2Ext_cmd_payload_addr(svBitVecVal* value_io_dma2Ext_cmd_payload_addr) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_dma2Ext_cmd_payload_addr\n"); );
    // Locals
    IData/*31:0*/ value_io_dma2Ext_cmd_payload_addr__Vcvt;
    value_io_dma2Ext_cmd_payload_addr__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_dma2Ext_cmd_payload_addr");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_dma2Ext_cmd_payload_addr_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_dma2Ext_cmd_payload_addr_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma2Ext_cmd_payload_addr__Vcvt);
    VL_SET_SVBV_I(32, value_io_dma2Ext_cmd_payload_addr, value_io_dma2Ext_cmd_payload_addr__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_dma2Ext_cmd_ready(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_dma2Ext_cmd_ready\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_dma2Ext_cmd_ready");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_dma2Ext_cmd_ready_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_dma2Ext_cmd_ready_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_dma2Ext_cmd_ready(const svBitVecVal* value_io_dma2Ext_cmd_ready) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_dma2Ext_cmd_ready\n"); );
    // Locals
    CData/*0:0*/ value_io_dma2Ext_cmd_ready__Vcvt;
    value_io_dma2Ext_cmd_ready__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_dma2Ext_cmd_ready");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_dma2Ext_cmd_ready_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_dma2Ext_cmd_ready_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_C_SVBV(1, value_io_dma2Ext_cmd_ready__Vcvt, value_io_dma2Ext_cmd_ready + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma2Ext_cmd_ready__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_dma2Ext_cmd_ready(svBitVecVal* value_io_dma2Ext_cmd_ready) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_dma2Ext_cmd_ready\n"); );
    // Locals
    CData/*0:0*/ value_io_dma2Ext_cmd_ready__Vcvt;
    value_io_dma2Ext_cmd_ready__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_dma2Ext_cmd_ready");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_dma2Ext_cmd_ready_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_dma2Ext_cmd_ready_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma2Ext_cmd_ready__Vcvt);
    VL_SET_SVBV_I(1, value_io_dma2Ext_cmd_ready, value_io_dma2Ext_cmd_ready__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_dma2Ext_cmd_valid(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_dma2Ext_cmd_valid\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_dma2Ext_cmd_valid");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_dma2Ext_cmd_valid_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_dma2Ext_cmd_valid_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_dma2Ext_cmd_valid(svBitVecVal* value_io_dma2Ext_cmd_valid) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_dma2Ext_cmd_valid\n"); );
    // Locals
    CData/*0:0*/ value_io_dma2Ext_cmd_valid__Vcvt;
    value_io_dma2Ext_cmd_valid__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_dma2Ext_cmd_valid");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_dma2Ext_cmd_valid_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_dma2Ext_cmd_valid_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma2Ext_cmd_valid__Vcvt);
    VL_SET_SVBV_I(1, value_io_dma2Ext_cmd_valid, value_io_dma2Ext_cmd_valid__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_dma2Ext_rsp_payload_rsp(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_dma2Ext_rsp_payload_rsp\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_dma2Ext_rsp_payload_rsp");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_dma2Ext_rsp_payload_rsp_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_dma2Ext_rsp_payload_rsp_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_dma2Ext_rsp_payload_rsp(const svBitVecVal* value_io_dma2Ext_rsp_payload_rsp) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_dma2Ext_rsp_payload_rsp\n"); );
    // Locals
    CData/*0:0*/ value_io_dma2Ext_rsp_payload_rsp__Vcvt;
    value_io_dma2Ext_rsp_payload_rsp__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_dma2Ext_rsp_payload_rsp");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_dma2Ext_rsp_payload_rsp_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_dma2Ext_rsp_payload_rsp_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_C_SVBV(1, value_io_dma2Ext_rsp_payload_rsp__Vcvt, value_io_dma2Ext_rsp_payload_rsp + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma2Ext_rsp_payload_rsp__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_dma2Ext_rsp_payload_rsp(svBitVecVal* value_io_dma2Ext_rsp_payload_rsp) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_dma2Ext_rsp_payload_rsp\n"); );
    // Locals
    CData/*0:0*/ value_io_dma2Ext_rsp_payload_rsp__Vcvt;
    value_io_dma2Ext_rsp_payload_rsp__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_dma2Ext_rsp_payload_rsp");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_dma2Ext_rsp_payload_rsp_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_dma2Ext_rsp_payload_rsp_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma2Ext_rsp_payload_rsp__Vcvt);
    VL_SET_SVBV_I(1, value_io_dma2Ext_rsp_payload_rsp, value_io_dma2Ext_rsp_payload_rsp__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_dma2Ext_rsp_payload_data(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_dma2Ext_rsp_payload_data\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_dma2Ext_rsp_payload_data");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_dma2Ext_rsp_payload_data_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_dma2Ext_rsp_payload_data_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_dma2Ext_rsp_payload_data(const svBitVecVal* value_io_dma2Ext_rsp_payload_data) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_dma2Ext_rsp_payload_data\n"); );
    // Locals
    VlWide<18>/*575:0*/ value_io_dma2Ext_rsp_payload_data__Vcvt;
    VL_ZERO_W(576, value_io_dma2Ext_rsp_payload_data__Vcvt);
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_dma2Ext_rsp_payload_data");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_dma2Ext_rsp_payload_data_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_dma2Ext_rsp_payload_data_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_W_SVBV(576, value_io_dma2Ext_rsp_payload_data__Vcvt, value_io_dma2Ext_rsp_payload_data + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma2Ext_rsp_payload_data__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_dma2Ext_rsp_payload_data(svBitVecVal* value_io_dma2Ext_rsp_payload_data) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_dma2Ext_rsp_payload_data\n"); );
    // Locals
    VlWide<18>/*575:0*/ value_io_dma2Ext_rsp_payload_data__Vcvt;
    VL_ZERO_W(576, value_io_dma2Ext_rsp_payload_data__Vcvt);
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_dma2Ext_rsp_payload_data");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_dma2Ext_rsp_payload_data_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_dma2Ext_rsp_payload_data_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma2Ext_rsp_payload_data__Vcvt);
    VL_SET_SVBV_W(576, value_io_dma2Ext_rsp_payload_data, value_io_dma2Ext_rsp_payload_data__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_dma2Ext_rsp_ready(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_dma2Ext_rsp_ready\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_dma2Ext_rsp_ready");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_dma2Ext_rsp_ready_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_dma2Ext_rsp_ready_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_dma2Ext_rsp_ready(svBitVecVal* value_io_dma2Ext_rsp_ready) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_dma2Ext_rsp_ready\n"); );
    // Locals
    CData/*0:0*/ value_io_dma2Ext_rsp_ready__Vcvt;
    value_io_dma2Ext_rsp_ready__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_dma2Ext_rsp_ready");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_dma2Ext_rsp_ready_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_dma2Ext_rsp_ready_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma2Ext_rsp_ready__Vcvt);
    VL_SET_SVBV_I(1, value_io_dma2Ext_rsp_ready, value_io_dma2Ext_rsp_ready__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_dma2Ext_rsp_valid(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_dma2Ext_rsp_valid\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_dma2Ext_rsp_valid");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_dma2Ext_rsp_valid_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_dma2Ext_rsp_valid_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_dma2Ext_rsp_valid(const svBitVecVal* value_io_dma2Ext_rsp_valid) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_dma2Ext_rsp_valid\n"); );
    // Locals
    CData/*0:0*/ value_io_dma2Ext_rsp_valid__Vcvt;
    value_io_dma2Ext_rsp_valid__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_dma2Ext_rsp_valid");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_dma2Ext_rsp_valid_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_dma2Ext_rsp_valid_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_C_SVBV(1, value_io_dma2Ext_rsp_valid__Vcvt, value_io_dma2Ext_rsp_valid + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma2Ext_rsp_valid__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_dma2Ext_rsp_valid(svBitVecVal* value_io_dma2Ext_rsp_valid) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_dma2Ext_rsp_valid\n"); );
    // Locals
    CData/*0:0*/ value_io_dma2Ext_rsp_valid__Vcvt;
    value_io_dma2Ext_rsp_valid__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_dma2Ext_rsp_valid");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_dma2Ext_rsp_valid_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_dma2Ext_rsp_valid_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma2Ext_rsp_valid__Vcvt);
    VL_SET_SVBV_I(1, value_io_dma2Ext_rsp_valid, value_io_dma2Ext_rsp_valid__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_dma1Ext_isWrite(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_dma1Ext_isWrite\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_dma1Ext_isWrite");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_dma1Ext_isWrite_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_dma1Ext_isWrite_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_dma1Ext_isWrite(svBitVecVal* value_io_dma1Ext_isWrite) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_dma1Ext_isWrite\n"); );
    // Locals
    CData/*0:0*/ value_io_dma1Ext_isWrite__Vcvt;
    value_io_dma1Ext_isWrite__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_dma1Ext_isWrite");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_dma1Ext_isWrite_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_dma1Ext_isWrite_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma1Ext_isWrite__Vcvt);
    VL_SET_SVBV_I(1, value_io_dma1Ext_isWrite, value_io_dma1Ext_isWrite__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_dma1Ext_grant(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_dma1Ext_grant\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_dma1Ext_grant");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_dma1Ext_grant_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_dma1Ext_grant_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_dma1Ext_grant(const svBitVecVal* value_io_dma1Ext_grant) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_dma1Ext_grant\n"); );
    // Locals
    CData/*0:0*/ value_io_dma1Ext_grant__Vcvt;
    value_io_dma1Ext_grant__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_dma1Ext_grant");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_dma1Ext_grant_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_dma1Ext_grant_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_C_SVBV(1, value_io_dma1Ext_grant__Vcvt, value_io_dma1Ext_grant + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma1Ext_grant__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_dma1Ext_grant(svBitVecVal* value_io_dma1Ext_grant) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_dma1Ext_grant\n"); );
    // Locals
    CData/*0:0*/ value_io_dma1Ext_grant__Vcvt;
    value_io_dma1Ext_grant__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_dma1Ext_grant");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_dma1Ext_grant_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_dma1Ext_grant_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma1Ext_grant__Vcvt);
    VL_SET_SVBV_I(1, value_io_dma1Ext_grant, value_io_dma1Ext_grant__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_dma1Ext_req(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_dma1Ext_req\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_dma1Ext_req");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_dma1Ext_req_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_dma1Ext_req_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_dma1Ext_req(svBitVecVal* value_io_dma1Ext_req) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_dma1Ext_req\n"); );
    // Locals
    CData/*0:0*/ value_io_dma1Ext_req__Vcvt;
    value_io_dma1Ext_req__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_dma1Ext_req");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_dma1Ext_req_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_dma1Ext_req_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma1Ext_req__Vcvt);
    VL_SET_SVBV_I(1, value_io_dma1Ext_req, value_io_dma1Ext_req__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_dma1Ext_len(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_dma1Ext_len\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_dma1Ext_len");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_dma1Ext_len_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_dma1Ext_len_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_dma1Ext_len(svBitVecVal* value_io_dma1Ext_len) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_dma1Ext_len\n"); );
    // Locals
    SData/*15:0*/ value_io_dma1Ext_len__Vcvt;
    value_io_dma1Ext_len__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_dma1Ext_len");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_dma1Ext_len_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_dma1Ext_len_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma1Ext_len__Vcvt);
    VL_SET_SVBV_I(16, value_io_dma1Ext_len, value_io_dma1Ext_len__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_dma1Ext_addr(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_dma1Ext_addr\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_dma1Ext_addr");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_dma1Ext_addr_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_dma1Ext_addr_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_dma1Ext_addr(svBitVecVal* value_io_dma1Ext_addr) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_dma1Ext_addr\n"); );
    // Locals
    IData/*31:0*/ value_io_dma1Ext_addr__Vcvt;
    value_io_dma1Ext_addr__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_dma1Ext_addr");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_dma1Ext_addr_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_dma1Ext_addr_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma1Ext_addr__Vcvt);
    VL_SET_SVBV_I(32, value_io_dma1Ext_addr, value_io_dma1Ext_addr__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_dma1Ext_writeData_payload(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_dma1Ext_writeData_payload\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_dma1Ext_writeData_payload");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_dma1Ext_writeData_payload_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_dma1Ext_writeData_payload_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_dma1Ext_writeData_payload(svBitVecVal* value_io_dma1Ext_writeData_payload) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_dma1Ext_writeData_payload\n"); );
    // Locals
    VlWide<18>/*575:0*/ value_io_dma1Ext_writeData_payload__Vcvt;
    VL_ZERO_W(576, value_io_dma1Ext_writeData_payload__Vcvt);
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_dma1Ext_writeData_payload");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_dma1Ext_writeData_payload_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_dma1Ext_writeData_payload_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma1Ext_writeData_payload__Vcvt);
    VL_SET_SVBV_W(576, value_io_dma1Ext_writeData_payload, value_io_dma1Ext_writeData_payload__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_dma1Ext_writeData_ready(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_dma1Ext_writeData_ready\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_dma1Ext_writeData_ready");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_dma1Ext_writeData_ready_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_dma1Ext_writeData_ready_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_dma1Ext_writeData_ready(const svBitVecVal* value_io_dma1Ext_writeData_ready) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_dma1Ext_writeData_ready\n"); );
    // Locals
    CData/*0:0*/ value_io_dma1Ext_writeData_ready__Vcvt;
    value_io_dma1Ext_writeData_ready__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_dma1Ext_writeData_ready");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_dma1Ext_writeData_ready_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_dma1Ext_writeData_ready_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_C_SVBV(1, value_io_dma1Ext_writeData_ready__Vcvt, value_io_dma1Ext_writeData_ready + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma1Ext_writeData_ready__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_dma1Ext_writeData_ready(svBitVecVal* value_io_dma1Ext_writeData_ready) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_dma1Ext_writeData_ready\n"); );
    // Locals
    CData/*0:0*/ value_io_dma1Ext_writeData_ready__Vcvt;
    value_io_dma1Ext_writeData_ready__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_dma1Ext_writeData_ready");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_dma1Ext_writeData_ready_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_dma1Ext_writeData_ready_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma1Ext_writeData_ready__Vcvt);
    VL_SET_SVBV_I(1, value_io_dma1Ext_writeData_ready, value_io_dma1Ext_writeData_ready__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_dma1Ext_writeData_valid(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_dma1Ext_writeData_valid\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_dma1Ext_writeData_valid");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_dma1Ext_writeData_valid_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_dma1Ext_writeData_valid_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_dma1Ext_writeData_valid(svBitVecVal* value_io_dma1Ext_writeData_valid) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_dma1Ext_writeData_valid\n"); );
    // Locals
    CData/*0:0*/ value_io_dma1Ext_writeData_valid__Vcvt;
    value_io_dma1Ext_writeData_valid__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_dma1Ext_writeData_valid");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_dma1Ext_writeData_valid_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_dma1Ext_writeData_valid_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma1Ext_writeData_valid__Vcvt);
    VL_SET_SVBV_I(1, value_io_dma1Ext_writeData_valid, value_io_dma1Ext_writeData_valid__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_dma1Ext_readData_payload(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_dma1Ext_readData_payload\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_dma1Ext_readData_payload");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_dma1Ext_readData_payload_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_dma1Ext_readData_payload_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_dma1Ext_readData_payload(const svBitVecVal* value_io_dma1Ext_readData_payload) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_dma1Ext_readData_payload\n"); );
    // Locals
    VlWide<18>/*575:0*/ value_io_dma1Ext_readData_payload__Vcvt;
    VL_ZERO_W(576, value_io_dma1Ext_readData_payload__Vcvt);
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_dma1Ext_readData_payload");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_dma1Ext_readData_payload_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_dma1Ext_readData_payload_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_W_SVBV(576, value_io_dma1Ext_readData_payload__Vcvt, value_io_dma1Ext_readData_payload + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma1Ext_readData_payload__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_dma1Ext_readData_payload(svBitVecVal* value_io_dma1Ext_readData_payload) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_dma1Ext_readData_payload\n"); );
    // Locals
    VlWide<18>/*575:0*/ value_io_dma1Ext_readData_payload__Vcvt;
    VL_ZERO_W(576, value_io_dma1Ext_readData_payload__Vcvt);
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_dma1Ext_readData_payload");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_dma1Ext_readData_payload_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_dma1Ext_readData_payload_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma1Ext_readData_payload__Vcvt);
    VL_SET_SVBV_W(576, value_io_dma1Ext_readData_payload, value_io_dma1Ext_readData_payload__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_dma1Ext_readData_ready(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_dma1Ext_readData_ready\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_dma1Ext_readData_ready");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_dma1Ext_readData_ready_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_dma1Ext_readData_ready_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_dma1Ext_readData_ready(svBitVecVal* value_io_dma1Ext_readData_ready) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_dma1Ext_readData_ready\n"); );
    // Locals
    CData/*0:0*/ value_io_dma1Ext_readData_ready__Vcvt;
    value_io_dma1Ext_readData_ready__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_dma1Ext_readData_ready");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_dma1Ext_readData_ready_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_dma1Ext_readData_ready_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma1Ext_readData_ready__Vcvt);
    VL_SET_SVBV_I(1, value_io_dma1Ext_readData_ready, value_io_dma1Ext_readData_ready__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_dma1Ext_readData_valid(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_dma1Ext_readData_valid\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_dma1Ext_readData_valid");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_dma1Ext_readData_valid_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_dma1Ext_readData_valid_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_dma1Ext_readData_valid(const svBitVecVal* value_io_dma1Ext_readData_valid) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_dma1Ext_readData_valid\n"); );
    // Locals
    CData/*0:0*/ value_io_dma1Ext_readData_valid__Vcvt;
    value_io_dma1Ext_readData_valid__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_dma1Ext_readData_valid");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_dma1Ext_readData_valid_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_dma1Ext_readData_valid_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_C_SVBV(1, value_io_dma1Ext_readData_valid__Vcvt, value_io_dma1Ext_readData_valid + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma1Ext_readData_valid__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_dma1Ext_readData_valid(svBitVecVal* value_io_dma1Ext_readData_valid) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_dma1Ext_readData_valid\n"); );
    // Locals
    CData/*0:0*/ value_io_dma1Ext_readData_valid__Vcvt;
    value_io_dma1Ext_readData_valid__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_dma1Ext_readData_valid");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_dma1Ext_readData_valid_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_dma1Ext_readData_valid_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma1Ext_readData_valid__Vcvt);
    VL_SET_SVBV_I(1, value_io_dma1Ext_readData_valid, value_io_dma1Ext_readData_valid__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_dma0Ext_cmd_payload_addr(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_dma0Ext_cmd_payload_addr\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_dma0Ext_cmd_payload_addr");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_dma0Ext_cmd_payload_addr_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_dma0Ext_cmd_payload_addr_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_dma0Ext_cmd_payload_addr(svBitVecVal* value_io_dma0Ext_cmd_payload_addr) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_dma0Ext_cmd_payload_addr\n"); );
    // Locals
    IData/*31:0*/ value_io_dma0Ext_cmd_payload_addr__Vcvt;
    value_io_dma0Ext_cmd_payload_addr__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_dma0Ext_cmd_payload_addr");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_dma0Ext_cmd_payload_addr_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_dma0Ext_cmd_payload_addr_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma0Ext_cmd_payload_addr__Vcvt);
    VL_SET_SVBV_I(32, value_io_dma0Ext_cmd_payload_addr, value_io_dma0Ext_cmd_payload_addr__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_dma0Ext_cmd_ready(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_dma0Ext_cmd_ready\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_dma0Ext_cmd_ready");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_dma0Ext_cmd_ready_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_dma0Ext_cmd_ready_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_dma0Ext_cmd_ready(const svBitVecVal* value_io_dma0Ext_cmd_ready) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_dma0Ext_cmd_ready\n"); );
    // Locals
    CData/*0:0*/ value_io_dma0Ext_cmd_ready__Vcvt;
    value_io_dma0Ext_cmd_ready__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_dma0Ext_cmd_ready");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_dma0Ext_cmd_ready_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_dma0Ext_cmd_ready_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_C_SVBV(1, value_io_dma0Ext_cmd_ready__Vcvt, value_io_dma0Ext_cmd_ready + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma0Ext_cmd_ready__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_dma0Ext_cmd_ready(svBitVecVal* value_io_dma0Ext_cmd_ready) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_dma0Ext_cmd_ready\n"); );
    // Locals
    CData/*0:0*/ value_io_dma0Ext_cmd_ready__Vcvt;
    value_io_dma0Ext_cmd_ready__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_dma0Ext_cmd_ready");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_dma0Ext_cmd_ready_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_dma0Ext_cmd_ready_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma0Ext_cmd_ready__Vcvt);
    VL_SET_SVBV_I(1, value_io_dma0Ext_cmd_ready, value_io_dma0Ext_cmd_ready__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_dma0Ext_cmd_valid(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_dma0Ext_cmd_valid\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_dma0Ext_cmd_valid");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_dma0Ext_cmd_valid_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_dma0Ext_cmd_valid_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_dma0Ext_cmd_valid(svBitVecVal* value_io_dma0Ext_cmd_valid) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_dma0Ext_cmd_valid\n"); );
    // Locals
    CData/*0:0*/ value_io_dma0Ext_cmd_valid__Vcvt;
    value_io_dma0Ext_cmd_valid__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_dma0Ext_cmd_valid");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_dma0Ext_cmd_valid_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_dma0Ext_cmd_valid_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma0Ext_cmd_valid__Vcvt);
    VL_SET_SVBV_I(1, value_io_dma0Ext_cmd_valid, value_io_dma0Ext_cmd_valid__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_dma0Ext_rsp_payload_rsp(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_dma0Ext_rsp_payload_rsp\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_dma0Ext_rsp_payload_rsp");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_dma0Ext_rsp_payload_rsp_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_dma0Ext_rsp_payload_rsp_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_dma0Ext_rsp_payload_rsp(const svBitVecVal* value_io_dma0Ext_rsp_payload_rsp) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_dma0Ext_rsp_payload_rsp\n"); );
    // Locals
    CData/*0:0*/ value_io_dma0Ext_rsp_payload_rsp__Vcvt;
    value_io_dma0Ext_rsp_payload_rsp__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_dma0Ext_rsp_payload_rsp");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_dma0Ext_rsp_payload_rsp_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_dma0Ext_rsp_payload_rsp_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_C_SVBV(1, value_io_dma0Ext_rsp_payload_rsp__Vcvt, value_io_dma0Ext_rsp_payload_rsp + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma0Ext_rsp_payload_rsp__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_dma0Ext_rsp_payload_rsp(svBitVecVal* value_io_dma0Ext_rsp_payload_rsp) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_dma0Ext_rsp_payload_rsp\n"); );
    // Locals
    CData/*0:0*/ value_io_dma0Ext_rsp_payload_rsp__Vcvt;
    value_io_dma0Ext_rsp_payload_rsp__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_dma0Ext_rsp_payload_rsp");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_dma0Ext_rsp_payload_rsp_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_dma0Ext_rsp_payload_rsp_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma0Ext_rsp_payload_rsp__Vcvt);
    VL_SET_SVBV_I(1, value_io_dma0Ext_rsp_payload_rsp, value_io_dma0Ext_rsp_payload_rsp__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_dma0Ext_rsp_payload_data(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_dma0Ext_rsp_payload_data\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_dma0Ext_rsp_payload_data");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_dma0Ext_rsp_payload_data_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_dma0Ext_rsp_payload_data_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_dma0Ext_rsp_payload_data(const svBitVecVal* value_io_dma0Ext_rsp_payload_data) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_dma0Ext_rsp_payload_data\n"); );
    // Locals
    VlWide<18>/*575:0*/ value_io_dma0Ext_rsp_payload_data__Vcvt;
    VL_ZERO_W(576, value_io_dma0Ext_rsp_payload_data__Vcvt);
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_dma0Ext_rsp_payload_data");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_dma0Ext_rsp_payload_data_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_dma0Ext_rsp_payload_data_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_W_SVBV(576, value_io_dma0Ext_rsp_payload_data__Vcvt, value_io_dma0Ext_rsp_payload_data + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma0Ext_rsp_payload_data__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_dma0Ext_rsp_payload_data(svBitVecVal* value_io_dma0Ext_rsp_payload_data) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_dma0Ext_rsp_payload_data\n"); );
    // Locals
    VlWide<18>/*575:0*/ value_io_dma0Ext_rsp_payload_data__Vcvt;
    VL_ZERO_W(576, value_io_dma0Ext_rsp_payload_data__Vcvt);
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_dma0Ext_rsp_payload_data");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_dma0Ext_rsp_payload_data_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_dma0Ext_rsp_payload_data_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma0Ext_rsp_payload_data__Vcvt);
    VL_SET_SVBV_W(576, value_io_dma0Ext_rsp_payload_data, value_io_dma0Ext_rsp_payload_data__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_dma0Ext_rsp_ready(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_dma0Ext_rsp_ready\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_dma0Ext_rsp_ready");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_dma0Ext_rsp_ready_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_dma0Ext_rsp_ready_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_dma0Ext_rsp_ready(svBitVecVal* value_io_dma0Ext_rsp_ready) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_dma0Ext_rsp_ready\n"); );
    // Locals
    CData/*0:0*/ value_io_dma0Ext_rsp_ready__Vcvt;
    value_io_dma0Ext_rsp_ready__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_dma0Ext_rsp_ready");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_dma0Ext_rsp_ready_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_dma0Ext_rsp_ready_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma0Ext_rsp_ready__Vcvt);
    VL_SET_SVBV_I(1, value_io_dma0Ext_rsp_ready, value_io_dma0Ext_rsp_ready__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_dma0Ext_rsp_valid(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_dma0Ext_rsp_valid\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_dma0Ext_rsp_valid");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_dma0Ext_rsp_valid_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_dma0Ext_rsp_valid_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_dma0Ext_rsp_valid(const svBitVecVal* value_io_dma0Ext_rsp_valid) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_dma0Ext_rsp_valid\n"); );
    // Locals
    CData/*0:0*/ value_io_dma0Ext_rsp_valid__Vcvt;
    value_io_dma0Ext_rsp_valid__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_dma0Ext_rsp_valid");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_dma0Ext_rsp_valid_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_dma0Ext_rsp_valid_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_C_SVBV(1, value_io_dma0Ext_rsp_valid__Vcvt, value_io_dma0Ext_rsp_valid + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma0Ext_rsp_valid__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_dma0Ext_rsp_valid(svBitVecVal* value_io_dma0Ext_rsp_valid) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_dma0Ext_rsp_valid\n"); );
    // Locals
    CData/*0:0*/ value_io_dma0Ext_rsp_valid__Vcvt;
    value_io_dma0Ext_rsp_valid__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_dma0Ext_rsp_valid");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_dma0Ext_rsp_valid_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_dma0Ext_rsp_valid_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_dma0Ext_rsp_valid__Vcvt);
    VL_SET_SVBV_I(1, value_io_dma0Ext_rsp_valid, value_io_dma0Ext_rsp_valid__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_resp_payload_err(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_resp_payload_err\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_resp_payload_err");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_resp_payload_err_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_resp_payload_err_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_resp_payload_err(svBitVecVal* value_io_resp_payload_err) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_resp_payload_err\n"); );
    // Locals
    CData/*0:0*/ value_io_resp_payload_err__Vcvt;
    value_io_resp_payload_err__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_resp_payload_err");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_resp_payload_err_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_resp_payload_err_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_resp_payload_err__Vcvt);
    VL_SET_SVBV_I(1, value_io_resp_payload_err, value_io_resp_payload_err__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_resp_payload_done(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_resp_payload_done\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_resp_payload_done");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_resp_payload_done_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_resp_payload_done_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_resp_payload_done(svBitVecVal* value_io_resp_payload_done) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_resp_payload_done\n"); );
    // Locals
    CData/*0:0*/ value_io_resp_payload_done__Vcvt;
    value_io_resp_payload_done__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_resp_payload_done");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_resp_payload_done_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_resp_payload_done_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_resp_payload_done__Vcvt);
    VL_SET_SVBV_I(1, value_io_resp_payload_done, value_io_resp_payload_done__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_resp_payload_data(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_resp_payload_data\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_resp_payload_data");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_resp_payload_data_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_resp_payload_data_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_resp_payload_data(svBitVecVal* value_io_resp_payload_data) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_resp_payload_data\n"); );
    // Locals
    IData/*31:0*/ value_io_resp_payload_data__Vcvt;
    value_io_resp_payload_data__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_resp_payload_data");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_resp_payload_data_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_resp_payload_data_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_resp_payload_data__Vcvt);
    VL_SET_SVBV_I(32, value_io_resp_payload_data, value_io_resp_payload_data__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_resp_ready(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_resp_ready\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_resp_ready");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_resp_ready_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_resp_ready_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_resp_ready(const svBitVecVal* value_io_resp_ready) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_resp_ready\n"); );
    // Locals
    CData/*0:0*/ value_io_resp_ready__Vcvt;
    value_io_resp_ready__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_resp_ready");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_resp_ready_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_resp_ready_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_C_SVBV(1, value_io_resp_ready__Vcvt, value_io_resp_ready + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_resp_ready__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_resp_ready(svBitVecVal* value_io_resp_ready) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_resp_ready\n"); );
    // Locals
    CData/*0:0*/ value_io_resp_ready__Vcvt;
    value_io_resp_ready__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_resp_ready");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_resp_ready_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_resp_ready_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_resp_ready__Vcvt);
    VL_SET_SVBV_I(1, value_io_resp_ready, value_io_resp_ready__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_resp_valid(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_resp_valid\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_resp_valid");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_resp_valid_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_resp_valid_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_resp_valid(svBitVecVal* value_io_resp_valid) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_resp_valid\n"); );
    // Locals
    CData/*0:0*/ value_io_resp_valid__Vcvt;
    value_io_resp_valid__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_resp_valid");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_resp_valid_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_resp_valid_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_resp_valid__Vcvt);
    VL_SET_SVBV_I(1, value_io_resp_valid, value_io_resp_valid__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_cmd_payload_data1(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_cmd_payload_data1\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_cmd_payload_data1");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_cmd_payload_data1_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_cmd_payload_data1_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_cmd_payload_data1(const svBitVecVal* value_io_cmd_payload_data1) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_cmd_payload_data1\n"); );
    // Locals
    IData/*31:0*/ value_io_cmd_payload_data1__Vcvt;
    value_io_cmd_payload_data1__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_cmd_payload_data1");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_cmd_payload_data1_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_cmd_payload_data1_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_I_SVBV(32, value_io_cmd_payload_data1__Vcvt, value_io_cmd_payload_data1 + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_cmd_payload_data1__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_cmd_payload_data1(svBitVecVal* value_io_cmd_payload_data1) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_cmd_payload_data1\n"); );
    // Locals
    IData/*31:0*/ value_io_cmd_payload_data1__Vcvt;
    value_io_cmd_payload_data1__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_cmd_payload_data1");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_cmd_payload_data1_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_cmd_payload_data1_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_cmd_payload_data1__Vcvt);
    VL_SET_SVBV_I(32, value_io_cmd_payload_data1, value_io_cmd_payload_data1__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_cmd_payload_data0(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_cmd_payload_data0\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_cmd_payload_data0");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_cmd_payload_data0_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_cmd_payload_data0_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_cmd_payload_data0(const svBitVecVal* value_io_cmd_payload_data0) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_cmd_payload_data0\n"); );
    // Locals
    IData/*31:0*/ value_io_cmd_payload_data0__Vcvt;
    value_io_cmd_payload_data0__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_cmd_payload_data0");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_cmd_payload_data0_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_cmd_payload_data0_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_I_SVBV(32, value_io_cmd_payload_data0__Vcvt, value_io_cmd_payload_data0 + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_cmd_payload_data0__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_cmd_payload_data0(svBitVecVal* value_io_cmd_payload_data0) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_cmd_payload_data0\n"); );
    // Locals
    IData/*31:0*/ value_io_cmd_payload_data0__Vcvt;
    value_io_cmd_payload_data0__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_cmd_payload_data0");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_cmd_payload_data0_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_cmd_payload_data0_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_cmd_payload_data0__Vcvt);
    VL_SET_SVBV_I(32, value_io_cmd_payload_data0, value_io_cmd_payload_data0__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_cmd_payload_reg1Addr(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_cmd_payload_reg1Addr\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_cmd_payload_reg1Addr");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_cmd_payload_reg1Addr_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_cmd_payload_reg1Addr_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_cmd_payload_reg1Addr(const svBitVecVal* value_io_cmd_payload_reg1Addr) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_cmd_payload_reg1Addr\n"); );
    // Locals
    CData/*4:0*/ value_io_cmd_payload_reg1Addr__Vcvt;
    value_io_cmd_payload_reg1Addr__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_cmd_payload_reg1Addr");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_cmd_payload_reg1Addr_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_cmd_payload_reg1Addr_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_C_SVBV(5, value_io_cmd_payload_reg1Addr__Vcvt, value_io_cmd_payload_reg1Addr + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_cmd_payload_reg1Addr__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_cmd_payload_reg1Addr(svBitVecVal* value_io_cmd_payload_reg1Addr) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_cmd_payload_reg1Addr\n"); );
    // Locals
    CData/*4:0*/ value_io_cmd_payload_reg1Addr__Vcvt;
    value_io_cmd_payload_reg1Addr__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_cmd_payload_reg1Addr");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_cmd_payload_reg1Addr_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_cmd_payload_reg1Addr_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_cmd_payload_reg1Addr__Vcvt);
    VL_SET_SVBV_I(5, value_io_cmd_payload_reg1Addr, value_io_cmd_payload_reg1Addr__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_cmd_payload_reg0Addr(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_cmd_payload_reg0Addr\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_cmd_payload_reg0Addr");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_cmd_payload_reg0Addr_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_cmd_payload_reg0Addr_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_cmd_payload_reg0Addr(const svBitVecVal* value_io_cmd_payload_reg0Addr) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_cmd_payload_reg0Addr\n"); );
    // Locals
    CData/*4:0*/ value_io_cmd_payload_reg0Addr__Vcvt;
    value_io_cmd_payload_reg0Addr__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_cmd_payload_reg0Addr");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_cmd_payload_reg0Addr_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_cmd_payload_reg0Addr_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_C_SVBV(5, value_io_cmd_payload_reg0Addr__Vcvt, value_io_cmd_payload_reg0Addr + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_cmd_payload_reg0Addr__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_cmd_payload_reg0Addr(svBitVecVal* value_io_cmd_payload_reg0Addr) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_cmd_payload_reg0Addr\n"); );
    // Locals
    CData/*4:0*/ value_io_cmd_payload_reg0Addr__Vcvt;
    value_io_cmd_payload_reg0Addr__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_cmd_payload_reg0Addr");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_cmd_payload_reg0Addr_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_cmd_payload_reg0Addr_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_cmd_payload_reg0Addr__Vcvt);
    VL_SET_SVBV_I(5, value_io_cmd_payload_reg0Addr, value_io_cmd_payload_reg0Addr__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_cmd_payload_op(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_cmd_payload_op\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_cmd_payload_op");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_cmd_payload_op_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_cmd_payload_op_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_cmd_payload_op(const svBitVecVal* value_io_cmd_payload_op) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_cmd_payload_op\n"); );
    // Locals
    CData/*1:0*/ value_io_cmd_payload_op__Vcvt;
    value_io_cmd_payload_op__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_cmd_payload_op");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_cmd_payload_op_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_cmd_payload_op_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_C_SVBV(2, value_io_cmd_payload_op__Vcvt, value_io_cmd_payload_op + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_cmd_payload_op__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_cmd_payload_op(svBitVecVal* value_io_cmd_payload_op) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_cmd_payload_op\n"); );
    // Locals
    CData/*1:0*/ value_io_cmd_payload_op__Vcvt;
    value_io_cmd_payload_op__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_cmd_payload_op");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_cmd_payload_op_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_cmd_payload_op_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_cmd_payload_op__Vcvt);
    VL_SET_SVBV_I(2, value_io_cmd_payload_op, value_io_cmd_payload_op__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_cmd_ready(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_cmd_ready\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_cmd_ready");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_cmd_ready_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_cmd_ready_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_cmd_ready(svBitVecVal* value_io_cmd_ready) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_cmd_ready\n"); );
    // Locals
    CData/*0:0*/ value_io_cmd_ready__Vcvt;
    value_io_cmd_ready__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_cmd_ready");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_cmd_ready_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_cmd_ready_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_cmd_ready__Vcvt);
    VL_SET_SVBV_I(1, value_io_cmd_ready, value_io_cmd_ready__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_cmd_valid(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_cmd_valid\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_cmd_valid");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_cmd_valid_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_cmd_valid_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_cmd_valid(const svBitVecVal* value_io_cmd_valid) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_cmd_valid\n"); );
    // Locals
    CData/*0:0*/ value_io_cmd_valid__Vcvt;
    value_io_cmd_valid__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_cmd_valid");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_cmd_valid_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_cmd_valid_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_C_SVBV(1, value_io_cmd_valid__Vcvt, value_io_cmd_valid + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_cmd_valid__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_cmd_valid(svBitVecVal* value_io_cmd_valid) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_cmd_valid\n"); );
    // Locals
    CData/*0:0*/ value_io_cmd_valid__Vcvt;
    value_io_cmd_valid__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_cmd_valid");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_cmd_valid_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_cmd_valid_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_cmd_valid__Vcvt);
    VL_SET_SVBV_I(1, value_io_cmd_valid, value_io_cmd_valid__Vcvt);
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
