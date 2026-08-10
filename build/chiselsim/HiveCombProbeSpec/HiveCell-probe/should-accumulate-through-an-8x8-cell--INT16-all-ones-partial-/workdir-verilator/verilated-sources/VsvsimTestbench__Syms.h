// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Symbol table internal header
//
// Internal details; most calling programs do not need this header,
// unless using verilator public meta comments.

#ifndef VERILATED_VSVSIMTESTBENCH__SYMS_H_
#define VERILATED_VSVSIMTESTBENCH__SYMS_H_  // guard

#include "verilated.h"

// INCLUDE MODEL CLASS

#include "VsvsimTestbench.h"

// INCLUDE MODULE CLASSES
#include "VsvsimTestbench___024root.h"
#include "VsvsimTestbench_HiveWorker.h"

// DPI TYPES for DPI Export callbacks (Internal use)
using VsvsimTestbench__Vcb_getBitWidthImpl_clock_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_aIn_0_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_aIn_1_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_aIn_2_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_aIn_3_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_aIn_4_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_aIn_5_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_aIn_6_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_aIn_7_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_aOut_0_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_aOut_1_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_aOut_2_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_aOut_3_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_aOut_4_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_aOut_5_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_aOut_6_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_aOut_7_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_cOut_0_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_cOut_1_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_cOut_2_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_cOut_3_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_cOut_4_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_cOut_5_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_cOut_6_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_cOut_7_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_clear_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_fmtIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_fmtOut_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_loadHIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_loadVIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_loadVLock_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_psumIn_0_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_psumIn_1_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_psumIn_2_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_psumIn_3_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_psumIn_4_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_psumIn_5_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_psumIn_6_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_psumIn_7_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_rndIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_rndOut_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_validIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_validOut_0_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_validOut_1_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_validOut_2_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_validOut_3_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_validOut_4_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_validOut_5_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_validOut_6_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_validOut_7_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_reset_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitsImpl_clock_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_clock);
using VsvsimTestbench__Vcb_getBitsImpl_io_aIn_0_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aIn_0);
using VsvsimTestbench__Vcb_getBitsImpl_io_aIn_1_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aIn_1);
using VsvsimTestbench__Vcb_getBitsImpl_io_aIn_2_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aIn_2);
using VsvsimTestbench__Vcb_getBitsImpl_io_aIn_3_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aIn_3);
using VsvsimTestbench__Vcb_getBitsImpl_io_aIn_4_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aIn_4);
using VsvsimTestbench__Vcb_getBitsImpl_io_aIn_5_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aIn_5);
using VsvsimTestbench__Vcb_getBitsImpl_io_aIn_6_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aIn_6);
using VsvsimTestbench__Vcb_getBitsImpl_io_aIn_7_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aIn_7);
using VsvsimTestbench__Vcb_getBitsImpl_io_aOut_0_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aOut_0);
using VsvsimTestbench__Vcb_getBitsImpl_io_aOut_1_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aOut_1);
using VsvsimTestbench__Vcb_getBitsImpl_io_aOut_2_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aOut_2);
using VsvsimTestbench__Vcb_getBitsImpl_io_aOut_3_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aOut_3);
using VsvsimTestbench__Vcb_getBitsImpl_io_aOut_4_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aOut_4);
using VsvsimTestbench__Vcb_getBitsImpl_io_aOut_5_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aOut_5);
using VsvsimTestbench__Vcb_getBitsImpl_io_aOut_6_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aOut_6);
using VsvsimTestbench__Vcb_getBitsImpl_io_aOut_7_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aOut_7);
using VsvsimTestbench__Vcb_getBitsImpl_io_cOut_0_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_cOut_0);
using VsvsimTestbench__Vcb_getBitsImpl_io_cOut_1_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_cOut_1);
using VsvsimTestbench__Vcb_getBitsImpl_io_cOut_2_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_cOut_2);
using VsvsimTestbench__Vcb_getBitsImpl_io_cOut_3_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_cOut_3);
using VsvsimTestbench__Vcb_getBitsImpl_io_cOut_4_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_cOut_4);
using VsvsimTestbench__Vcb_getBitsImpl_io_cOut_5_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_cOut_5);
using VsvsimTestbench__Vcb_getBitsImpl_io_cOut_6_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_cOut_6);
using VsvsimTestbench__Vcb_getBitsImpl_io_cOut_7_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_cOut_7);
using VsvsimTestbench__Vcb_getBitsImpl_io_clear_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_clear);
using VsvsimTestbench__Vcb_getBitsImpl_io_fmtIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*1:0*/ &value_io_fmtIn);
using VsvsimTestbench__Vcb_getBitsImpl_io_fmtOut_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*1:0*/ &value_io_fmtOut);
using VsvsimTestbench__Vcb_getBitsImpl_io_loadHIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_loadHIn);
using VsvsimTestbench__Vcb_getBitsImpl_io_loadVIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_loadVIn);
using VsvsimTestbench__Vcb_getBitsImpl_io_loadVLock_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_loadVLock);
using VsvsimTestbench__Vcb_getBitsImpl_io_psumIn_0_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_psumIn_0);
using VsvsimTestbench__Vcb_getBitsImpl_io_psumIn_1_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_psumIn_1);
using VsvsimTestbench__Vcb_getBitsImpl_io_psumIn_2_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_psumIn_2);
using VsvsimTestbench__Vcb_getBitsImpl_io_psumIn_3_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_psumIn_3);
using VsvsimTestbench__Vcb_getBitsImpl_io_psumIn_4_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_psumIn_4);
using VsvsimTestbench__Vcb_getBitsImpl_io_psumIn_5_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_psumIn_5);
using VsvsimTestbench__Vcb_getBitsImpl_io_psumIn_6_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_psumIn_6);
using VsvsimTestbench__Vcb_getBitsImpl_io_psumIn_7_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_psumIn_7);
using VsvsimTestbench__Vcb_getBitsImpl_io_rndIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*2:0*/ &value_io_rndIn);
using VsvsimTestbench__Vcb_getBitsImpl_io_rndOut_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*2:0*/ &value_io_rndOut);
using VsvsimTestbench__Vcb_getBitsImpl_io_validIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_validIn);
using VsvsimTestbench__Vcb_getBitsImpl_io_validOut_0_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_validOut_0);
using VsvsimTestbench__Vcb_getBitsImpl_io_validOut_1_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_validOut_1);
using VsvsimTestbench__Vcb_getBitsImpl_io_validOut_2_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_validOut_2);
using VsvsimTestbench__Vcb_getBitsImpl_io_validOut_3_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_validOut_3);
using VsvsimTestbench__Vcb_getBitsImpl_io_validOut_4_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_validOut_4);
using VsvsimTestbench__Vcb_getBitsImpl_io_validOut_5_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_validOut_5);
using VsvsimTestbench__Vcb_getBitsImpl_io_validOut_6_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_validOut_6);
using VsvsimTestbench__Vcb_getBitsImpl_io_validOut_7_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_validOut_7);
using VsvsimTestbench__Vcb_getBitsImpl_reset_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_reset);
using VsvsimTestbench__Vcb_setBitsImpl_clock_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ value_clock);
using VsvsimTestbench__Vcb_setBitsImpl_io_aIn_0_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ value_io_aIn_0);
using VsvsimTestbench__Vcb_setBitsImpl_io_aIn_1_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ value_io_aIn_1);
using VsvsimTestbench__Vcb_setBitsImpl_io_aIn_2_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ value_io_aIn_2);
using VsvsimTestbench__Vcb_setBitsImpl_io_aIn_3_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ value_io_aIn_3);
using VsvsimTestbench__Vcb_setBitsImpl_io_aIn_4_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ value_io_aIn_4);
using VsvsimTestbench__Vcb_setBitsImpl_io_aIn_5_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ value_io_aIn_5);
using VsvsimTestbench__Vcb_setBitsImpl_io_aIn_6_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ value_io_aIn_6);
using VsvsimTestbench__Vcb_setBitsImpl_io_aIn_7_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ value_io_aIn_7);
using VsvsimTestbench__Vcb_setBitsImpl_io_clear_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ value_io_clear);
using VsvsimTestbench__Vcb_setBitsImpl_io_fmtIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*1:0*/ value_io_fmtIn);
using VsvsimTestbench__Vcb_setBitsImpl_io_loadHIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ value_io_loadHIn);
using VsvsimTestbench__Vcb_setBitsImpl_io_loadVIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ value_io_loadVIn);
using VsvsimTestbench__Vcb_setBitsImpl_io_loadVLock_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ value_io_loadVLock);
using VsvsimTestbench__Vcb_setBitsImpl_io_psumIn_0_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ value_io_psumIn_0);
using VsvsimTestbench__Vcb_setBitsImpl_io_psumIn_1_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ value_io_psumIn_1);
using VsvsimTestbench__Vcb_setBitsImpl_io_psumIn_2_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ value_io_psumIn_2);
using VsvsimTestbench__Vcb_setBitsImpl_io_psumIn_3_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ value_io_psumIn_3);
using VsvsimTestbench__Vcb_setBitsImpl_io_psumIn_4_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ value_io_psumIn_4);
using VsvsimTestbench__Vcb_setBitsImpl_io_psumIn_5_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ value_io_psumIn_5);
using VsvsimTestbench__Vcb_setBitsImpl_io_psumIn_6_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ value_io_psumIn_6);
using VsvsimTestbench__Vcb_setBitsImpl_io_psumIn_7_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ value_io_psumIn_7);
using VsvsimTestbench__Vcb_setBitsImpl_io_rndIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*2:0*/ value_io_rndIn);
using VsvsimTestbench__Vcb_setBitsImpl_io_validIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ value_io_validIn);
using VsvsimTestbench__Vcb_setBitsImpl_reset_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ value_reset);
using VsvsimTestbench__Vcb_simulation_disableTrace_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &success);
using VsvsimTestbench__Vcb_simulation_enableTrace_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &success);
using VsvsimTestbench__Vcb_simulation_initializeTrace_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, std::string traceFilePath);

// SYMS CLASS (contains all model state)
class alignas(VL_CACHE_LINE_BYTES) VsvsimTestbench__Syms final : public VerilatedSyms {
  public:
    // INTERNAL STATE
    VsvsimTestbench* const __Vm_modelp;
    VlDeleter __Vm_deleter;
    bool __Vm_didInit = false;

    // MODULE INSTANCE STATE
    VsvsimTestbench___024root      TOP;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_0_0;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_0_1;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_0_2;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_0_3;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_0_4;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_0_5;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_0_6;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_0_7;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_1_0;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_1_1;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_1_2;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_1_3;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_1_4;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_1_5;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_1_6;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_1_7;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_2_0;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_2_1;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_2_2;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_2_3;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_2_4;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_2_5;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_2_6;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_2_7;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_3_0;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_3_1;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_3_2;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_3_3;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_3_4;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_3_5;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_3_6;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_3_7;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_4_0;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_4_1;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_4_2;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_4_3;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_4_4;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_4_5;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_4_6;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_4_7;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_5_0;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_5_1;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_5_2;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_5_3;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_5_4;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_5_5;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_5_6;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_5_7;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_6_0;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_6_1;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_6_2;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_6_3;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_6_4;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_6_5;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_6_6;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_6_7;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_7_0;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_7_1;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_7_2;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_7_3;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_7_4;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_7_5;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_7_6;
    VsvsimTestbench_HiveWorker     TOP__svsimTestbench__DOT__dut__DOT__pes_7_7;

    // SCOPE NAMES
    VerilatedScope* __Vscopep_svsimTestbench;

    // CONSTRUCTORS
    VsvsimTestbench__Syms(VerilatedContext* contextp, const char* namep, VsvsimTestbench* modelp);
    ~VsvsimTestbench__Syms();

    // METHODS
    const char* name() const { return TOP.vlNamep; }
};

#endif  // guard
