// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See VsvsimTestbench.h for the primary calling header

#include "VsvsimTestbench__pch.h"

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_7__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_7__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_6.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__psumReg = 0U;
        vlSelfRef.__PVT__wReg = 0U;
    } else {
        if (vlSymsp->TOP.svsimTestbench__DOT__io_clear) {
            vlSelfRef.__PVT__psumReg = 0U;
        } else if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_0.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_0.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_10) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_10) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_10)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP.svsimTestbench__DOT__io_aIn_10;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_10) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_10) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_10))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_10) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_10) 
                                             << 0x0000000dU)))));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.__PVT__wReg) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.__PVT__wReg))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.__PVT__wReg) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.__PVT__wReg) 
                                             << 0x0000000dU)))));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut 
                    = ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        ^ vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32) 
                       >> 0x1fU);
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                    = (0x0000ffffffffffffULL & ((0x0000000000800000ULL 
                                                 | (QData)((IData)(
                                                                   (0x007fffffU 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                                * (0x0000000000800000ULL 
                                                   | (QData)((IData)(
                                                                     (0x007fffffU 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2 
                    = (0x000001ffU & (((0x000000ffU 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                           >> 0x17U)) 
                                       + (0x000000ffU 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                             >> 0x17U))) 
                                      + (1U & (IData)(
                                                      (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                       >> 0x2fU)))));
                if ((1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                   >> 0x2fU)))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x18U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x17U)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x17U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x16U)));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1 
                    = (2U == (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN 
                    = (3U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0 
                    = (1U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                    = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                        | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? ((((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5) 
                                               >> 0x0fU)))) 
                             << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5)) 
                           * (((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5) 
                                                 >> 0x0fU)))) 
                               << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5)))
                        : ((1U & (((~ ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) 
                                       | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1))) 
                                   | (IData)((0U == 
                                              (0x7fffffffU 
                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                  | (IData)((0U == 
                                             (0x7fffffffU 
                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32)))))
                            ? 0U : (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                     << 0x0000001fU) 
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2) 
                                                - (IData)(0x7fU)))
                                          : 0U) << 0x00000017U) 
                                       | (0x007fffffU 
                                          & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                              & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                  ? 
                                                 ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))
                                                  : 
                                                 (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                   | (~ (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut))) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))))
                                              ? ((IData)(1U) 
                                                 + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm)
                                              : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T 
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_0.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_0.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_0.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_0.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_0.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_0.__PVT__psumReg 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                    = (0x03ffffffU & ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                    = (0x07ffffffU & (((0x04000000U 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                                           << 1U)) 
                                       | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed) 
                                      + ((0x04000000U 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                                             << 1U)) 
                                         | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                    = (0x03ffffffU & ((0x04000000U 
                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       ? (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero 
                    = ((0x02000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                        ? 0U : ((0x01000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                 ? 1U : ((0x00800000U 
                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                          ? 2U : ((0x00400000U 
                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 3U
                                                   : 
                                                  ((0x00200000U 
                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                    ? 4U
                                                    : 
                                                   ((0x00100000U 
                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                     ? 5U
                                                     : 
                                                    ((0x00080000U 
                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                      ? 6U
                                                      : 
                                                     ((0x00040000U 
                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                       ? 7U
                                                       : 
                                                      ((0x00020000U 
                                                        & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                        ? 8U
                                                        : 
                                                       ((0x00010000U 
                                                         & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                         ? 9U
                                                         : 
                                                        ((0x00008000U 
                                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                          ? 0x0aU
                                                          : 
                                                         ((0x00004000U 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                           ? 0x0bU
                                                           : 
                                                          ((0x00002000U 
                                                            & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                            ? 0x0cU
                                                            : 
                                                           ((0x00001000U 
                                                             & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                             ? 0x0dU
                                                             : 
                                                            ((0x00000800U 
                                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                              ? 0x0eU
                                                              : 
                                                             ((0x00000400U 
                                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                               ? 0x0fU
                                                               : 
                                                              ((0x00000200U 
                                                                & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                ? 0x10U
                                                                : 
                                                               ((0x00000100U 
                                                                 & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                 ? 0x11U
                                                                 : 
                                                                ((0x00000080U 
                                                                  & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                  ? 0x12U
                                                                  : 
                                                                 ((0x00000040U 
                                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                   ? 0x13U
                                                                   : 
                                                                  ((0x00000020U 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                    ? 0x14U
                                                                    : 
                                                                   ((0x00000010U 
                                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                     ? 0x15U
                                                                     : 
                                                                    ((8U 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                      ? 0x16U
                                                                      : 
                                                                     ((4U 
                                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                       ? 0x17U
                                                                       : 
                                                                      (0x00000018U 
                                                                       | (1U 
                                                                          & (~ 
                                                                             (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                                                                              >> 1U))))))))))))))))))))))))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                    = (0x01ffffffffffffffULL & ((QData)((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)) 
                                                << (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3 
                    = (0x000000ffU & ((IData)(1U) + 
                                      ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                        ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_0.__PVT__psumReg 
                                           >> 0x17U)
                                        : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                           >> 0x17U))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_0.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_0.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_0.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                            - (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1)))
                                                         : 0U) 
                                                       << 0x00000017U) 
                                                      | (0x007fffffU 
                                                         & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                                 ? 
                                                                ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                  >> 0x1aU) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))
                                                                 : 
                                                                (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                                  | (~ 
                                                                     (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                      >> 0x1aU))) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))))
                                                             ? 
                                                            ((IData)(1U) 
                                                             + (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U)))
                                                             : (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U))))))))));
            }
        }
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_0.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP.svsimTestbench__DOT__io_fmtIn;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP.svsimTestbench__DOT__io_rndIn;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_0.__PVT__io_validOut_REG));
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP.svsimTestbench__DOT__io_fmtIn;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP.svsimTestbench__DOT__io_rndIn;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_10));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__psumReg = 0U;
        vlSelfRef.__PVT__wReg = 0U;
    } else {
        if (vlSymsp->TOP.svsimTestbench__DOT__io_clear) {
            vlSelfRef.__PVT__psumReg = 0U;
        } else if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_1.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__io_aOut_REG) 
                                             << 0x0000000dU)))));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.__PVT__wReg) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.__PVT__wReg))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.__PVT__wReg) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.__PVT__wReg) 
                                             << 0x0000000dU)))));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut 
                    = ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        ^ vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32) 
                       >> 0x1fU);
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                    = (0x0000ffffffffffffULL & ((0x0000000000800000ULL 
                                                 | (QData)((IData)(
                                                                   (0x007fffffU 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                                * (0x0000000000800000ULL 
                                                   | (QData)((IData)(
                                                                     (0x007fffffU 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2 
                    = (0x000001ffU & (((0x000000ffU 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                           >> 0x17U)) 
                                       + (0x000000ffU 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                             >> 0x17U))) 
                                      + (1U & (IData)(
                                                      (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                       >> 0x2fU)))));
                if ((1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                   >> 0x2fU)))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x18U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x17U)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x17U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x16U)));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1 
                    = (2U == (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN 
                    = (3U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0 
                    = (1U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                    = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                        | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? ((((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5) 
                                               >> 0x0fU)))) 
                             << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5)) 
                           * (((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5) 
                                                 >> 0x0fU)))) 
                               << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5)))
                        : ((1U & (((~ ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) 
                                       | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1))) 
                                   | (IData)((0U == 
                                              (0x7fffffffU 
                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                  | (IData)((0U == 
                                             (0x7fffffffU 
                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32)))))
                            ? 0U : (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                     << 0x0000001fU) 
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2) 
                                                - (IData)(0x7fU)))
                                          : 0U) << 0x00000017U) 
                                       | (0x007fffffU 
                                          & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                              & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                  ? 
                                                 ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))
                                                  : 
                                                 (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                   | (~ (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut))) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))))
                                              ? ((IData)(1U) 
                                                 + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm)
                                              : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T 
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_1.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_1.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_1.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_1.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_1.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_1.__PVT__psumReg 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                    = (0x03ffffffU & ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                    = (0x07ffffffU & (((0x04000000U 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                                           << 1U)) 
                                       | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed) 
                                      + ((0x04000000U 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                                             << 1U)) 
                                         | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                    = (0x03ffffffU & ((0x04000000U 
                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       ? (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero 
                    = ((0x02000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                        ? 0U : ((0x01000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                 ? 1U : ((0x00800000U 
                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                          ? 2U : ((0x00400000U 
                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 3U
                                                   : 
                                                  ((0x00200000U 
                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                    ? 4U
                                                    : 
                                                   ((0x00100000U 
                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                     ? 5U
                                                     : 
                                                    ((0x00080000U 
                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                      ? 6U
                                                      : 
                                                     ((0x00040000U 
                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                       ? 7U
                                                       : 
                                                      ((0x00020000U 
                                                        & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                        ? 8U
                                                        : 
                                                       ((0x00010000U 
                                                         & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                         ? 9U
                                                         : 
                                                        ((0x00008000U 
                                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                          ? 0x0aU
                                                          : 
                                                         ((0x00004000U 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                           ? 0x0bU
                                                           : 
                                                          ((0x00002000U 
                                                            & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                            ? 0x0cU
                                                            : 
                                                           ((0x00001000U 
                                                             & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                             ? 0x0dU
                                                             : 
                                                            ((0x00000800U 
                                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                              ? 0x0eU
                                                              : 
                                                             ((0x00000400U 
                                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                               ? 0x0fU
                                                               : 
                                                              ((0x00000200U 
                                                                & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                ? 0x10U
                                                                : 
                                                               ((0x00000100U 
                                                                 & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                 ? 0x11U
                                                                 : 
                                                                ((0x00000080U 
                                                                  & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                  ? 0x12U
                                                                  : 
                                                                 ((0x00000040U 
                                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                   ? 0x13U
                                                                   : 
                                                                  ((0x00000020U 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                    ? 0x14U
                                                                    : 
                                                                   ((0x00000010U 
                                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                     ? 0x15U
                                                                     : 
                                                                    ((8U 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                      ? 0x16U
                                                                      : 
                                                                     ((4U 
                                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                       ? 0x17U
                                                                       : 
                                                                      (0x00000018U 
                                                                       | (1U 
                                                                          & (~ 
                                                                             (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                                                                              >> 1U))))))))))))))))))))))))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                    = (0x01ffffffffffffffULL & ((QData)((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)) 
                                                << (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3 
                    = (0x000000ffU & ((IData)(1U) + 
                                      ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                        ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_1.__PVT__psumReg 
                                           >> 0x17U)
                                        : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                           >> 0x17U))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_1.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_1.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_1.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                            - (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1)))
                                                         : 0U) 
                                                       << 0x00000017U) 
                                                      | (0x007fffffU 
                                                         & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                                 ? 
                                                                ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                  >> 0x1aU) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))
                                                                 : 
                                                                (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                                  | (~ 
                                                                     (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                      >> 0x1aU))) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))))
                                                             ? 
                                                            ((IData)(1U) 
                                                             + (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U)))
                                                             : (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U))))))))));
            }
        }
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_1.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__io_validOut_REG));
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__psumReg = 0U;
        vlSelfRef.__PVT__wReg = 0U;
    } else {
        if (vlSymsp->TOP.svsimTestbench__DOT__io_clear) {
            vlSelfRef.__PVT__psumReg = 0U;
        } else if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_2.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__io_aOut_REG) 
                                             << 0x0000000dU)))));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.__PVT__wReg) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.__PVT__wReg))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.__PVT__wReg) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.__PVT__wReg) 
                                             << 0x0000000dU)))));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut 
                    = ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        ^ vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32) 
                       >> 0x1fU);
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                    = (0x0000ffffffffffffULL & ((0x0000000000800000ULL 
                                                 | (QData)((IData)(
                                                                   (0x007fffffU 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                                * (0x0000000000800000ULL 
                                                   | (QData)((IData)(
                                                                     (0x007fffffU 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2 
                    = (0x000001ffU & (((0x000000ffU 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                           >> 0x17U)) 
                                       + (0x000000ffU 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                             >> 0x17U))) 
                                      + (1U & (IData)(
                                                      (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                       >> 0x2fU)))));
                if ((1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                   >> 0x2fU)))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x18U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x17U)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x17U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x16U)));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1 
                    = (2U == (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN 
                    = (3U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0 
                    = (1U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                    = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                        | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? ((((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5) 
                                               >> 0x0fU)))) 
                             << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5)) 
                           * (((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5) 
                                                 >> 0x0fU)))) 
                               << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5)))
                        : ((1U & (((~ ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) 
                                       | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1))) 
                                   | (IData)((0U == 
                                              (0x7fffffffU 
                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                  | (IData)((0U == 
                                             (0x7fffffffU 
                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32)))))
                            ? 0U : (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                     << 0x0000001fU) 
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2) 
                                                - (IData)(0x7fU)))
                                          : 0U) << 0x00000017U) 
                                       | (0x007fffffU 
                                          & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                              & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                  ? 
                                                 ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))
                                                  : 
                                                 (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                   | (~ (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut))) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))))
                                              ? ((IData)(1U) 
                                                 + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm)
                                              : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T 
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_2.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_2.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_2.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_2.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_2.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_2.__PVT__psumReg 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                    = (0x03ffffffU & ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                    = (0x07ffffffU & (((0x04000000U 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                                           << 1U)) 
                                       | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed) 
                                      + ((0x04000000U 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                                             << 1U)) 
                                         | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                    = (0x03ffffffU & ((0x04000000U 
                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       ? (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero 
                    = ((0x02000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                        ? 0U : ((0x01000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                 ? 1U : ((0x00800000U 
                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                          ? 2U : ((0x00400000U 
                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 3U
                                                   : 
                                                  ((0x00200000U 
                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                    ? 4U
                                                    : 
                                                   ((0x00100000U 
                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                     ? 5U
                                                     : 
                                                    ((0x00080000U 
                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                      ? 6U
                                                      : 
                                                     ((0x00040000U 
                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                       ? 7U
                                                       : 
                                                      ((0x00020000U 
                                                        & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                        ? 8U
                                                        : 
                                                       ((0x00010000U 
                                                         & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                         ? 9U
                                                         : 
                                                        ((0x00008000U 
                                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                          ? 0x0aU
                                                          : 
                                                         ((0x00004000U 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                           ? 0x0bU
                                                           : 
                                                          ((0x00002000U 
                                                            & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                            ? 0x0cU
                                                            : 
                                                           ((0x00001000U 
                                                             & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                             ? 0x0dU
                                                             : 
                                                            ((0x00000800U 
                                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                              ? 0x0eU
                                                              : 
                                                             ((0x00000400U 
                                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                               ? 0x0fU
                                                               : 
                                                              ((0x00000200U 
                                                                & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                ? 0x10U
                                                                : 
                                                               ((0x00000100U 
                                                                 & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                 ? 0x11U
                                                                 : 
                                                                ((0x00000080U 
                                                                  & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                  ? 0x12U
                                                                  : 
                                                                 ((0x00000040U 
                                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                   ? 0x13U
                                                                   : 
                                                                  ((0x00000020U 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                    ? 0x14U
                                                                    : 
                                                                   ((0x00000010U 
                                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                     ? 0x15U
                                                                     : 
                                                                    ((8U 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                      ? 0x16U
                                                                      : 
                                                                     ((4U 
                                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                       ? 0x17U
                                                                       : 
                                                                      (0x00000018U 
                                                                       | (1U 
                                                                          & (~ 
                                                                             (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                                                                              >> 1U))))))))))))))))))))))))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                    = (0x01ffffffffffffffULL & ((QData)((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)) 
                                                << (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3 
                    = (0x000000ffU & ((IData)(1U) + 
                                      ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                        ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_2.__PVT__psumReg 
                                           >> 0x17U)
                                        : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                           >> 0x17U))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_2.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_2.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_2.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                            - (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1)))
                                                         : 0U) 
                                                       << 0x00000017U) 
                                                      | (0x007fffffU 
                                                         & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                                 ? 
                                                                ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                  >> 0x1aU) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))
                                                                 : 
                                                                (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                                  | (~ 
                                                                     (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                      >> 0x1aU))) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))))
                                                             ? 
                                                            ((IData)(1U) 
                                                             + (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U)))
                                                             : (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U))))))))));
            }
        }
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_2.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__io_validOut_REG));
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__psumReg = 0U;
        vlSelfRef.__PVT__wReg = 0U;
    } else {
        if (vlSymsp->TOP.svsimTestbench__DOT__io_clear) {
            vlSelfRef.__PVT__psumReg = 0U;
        } else if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_3.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__io_aOut_REG) 
                                             << 0x0000000dU)))));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.__PVT__wReg) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.__PVT__wReg))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.__PVT__wReg) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.__PVT__wReg) 
                                             << 0x0000000dU)))));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut 
                    = ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        ^ vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32) 
                       >> 0x1fU);
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                    = (0x0000ffffffffffffULL & ((0x0000000000800000ULL 
                                                 | (QData)((IData)(
                                                                   (0x007fffffU 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                                * (0x0000000000800000ULL 
                                                   | (QData)((IData)(
                                                                     (0x007fffffU 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2 
                    = (0x000001ffU & (((0x000000ffU 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                           >> 0x17U)) 
                                       + (0x000000ffU 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                             >> 0x17U))) 
                                      + (1U & (IData)(
                                                      (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                       >> 0x2fU)))));
                if ((1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                   >> 0x2fU)))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x18U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x17U)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x17U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x16U)));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1 
                    = (2U == (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN 
                    = (3U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0 
                    = (1U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                    = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                        | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? ((((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5) 
                                               >> 0x0fU)))) 
                             << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5)) 
                           * (((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5) 
                                                 >> 0x0fU)))) 
                               << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5)))
                        : ((1U & (((~ ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) 
                                       | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1))) 
                                   | (IData)((0U == 
                                              (0x7fffffffU 
                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                  | (IData)((0U == 
                                             (0x7fffffffU 
                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32)))))
                            ? 0U : (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                     << 0x0000001fU) 
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2) 
                                                - (IData)(0x7fU)))
                                          : 0U) << 0x00000017U) 
                                       | (0x007fffffU 
                                          & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                              & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                  ? 
                                                 ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))
                                                  : 
                                                 (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                   | (~ (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut))) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))))
                                              ? ((IData)(1U) 
                                                 + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm)
                                              : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T 
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_3.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_3.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_3.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_3.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_3.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_3.__PVT__psumReg 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                    = (0x03ffffffU & ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                    = (0x07ffffffU & (((0x04000000U 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                                           << 1U)) 
                                       | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed) 
                                      + ((0x04000000U 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                                             << 1U)) 
                                         | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                    = (0x03ffffffU & ((0x04000000U 
                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       ? (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero 
                    = ((0x02000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                        ? 0U : ((0x01000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                 ? 1U : ((0x00800000U 
                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                          ? 2U : ((0x00400000U 
                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 3U
                                                   : 
                                                  ((0x00200000U 
                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                    ? 4U
                                                    : 
                                                   ((0x00100000U 
                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                     ? 5U
                                                     : 
                                                    ((0x00080000U 
                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                      ? 6U
                                                      : 
                                                     ((0x00040000U 
                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                       ? 7U
                                                       : 
                                                      ((0x00020000U 
                                                        & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                        ? 8U
                                                        : 
                                                       ((0x00010000U 
                                                         & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                         ? 9U
                                                         : 
                                                        ((0x00008000U 
                                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                          ? 0x0aU
                                                          : 
                                                         ((0x00004000U 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                           ? 0x0bU
                                                           : 
                                                          ((0x00002000U 
                                                            & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                            ? 0x0cU
                                                            : 
                                                           ((0x00001000U 
                                                             & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                             ? 0x0dU
                                                             : 
                                                            ((0x00000800U 
                                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                              ? 0x0eU
                                                              : 
                                                             ((0x00000400U 
                                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                               ? 0x0fU
                                                               : 
                                                              ((0x00000200U 
                                                                & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                ? 0x10U
                                                                : 
                                                               ((0x00000100U 
                                                                 & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                 ? 0x11U
                                                                 : 
                                                                ((0x00000080U 
                                                                  & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                  ? 0x12U
                                                                  : 
                                                                 ((0x00000040U 
                                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                   ? 0x13U
                                                                   : 
                                                                  ((0x00000020U 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                    ? 0x14U
                                                                    : 
                                                                   ((0x00000010U 
                                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                     ? 0x15U
                                                                     : 
                                                                    ((8U 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                      ? 0x16U
                                                                      : 
                                                                     ((4U 
                                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                       ? 0x17U
                                                                       : 
                                                                      (0x00000018U 
                                                                       | (1U 
                                                                          & (~ 
                                                                             (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                                                                              >> 1U))))))))))))))))))))))))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                    = (0x01ffffffffffffffULL & ((QData)((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)) 
                                                << (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3 
                    = (0x000000ffU & ((IData)(1U) + 
                                      ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                        ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_3.__PVT__psumReg 
                                           >> 0x17U)
                                        : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                           >> 0x17U))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_3.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_3.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_3.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                            - (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1)))
                                                         : 0U) 
                                                       << 0x00000017U) 
                                                      | (0x007fffffU 
                                                         & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                                 ? 
                                                                ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                  >> 0x1aU) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))
                                                                 : 
                                                                (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                                  | (~ 
                                                                     (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                      >> 0x1aU))) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))))
                                                             ? 
                                                            ((IData)(1U) 
                                                             + (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U)))
                                                             : (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U))))))))));
            }
        }
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_3.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__io_validOut_REG));
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__psumReg = 0U;
        vlSelfRef.__PVT__wReg = 0U;
    } else {
        if (vlSymsp->TOP.svsimTestbench__DOT__io_clear) {
            vlSelfRef.__PVT__psumReg = 0U;
        } else if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_4.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__io_aOut_REG) 
                                             << 0x0000000dU)))));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.__PVT__wReg) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.__PVT__wReg))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.__PVT__wReg) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.__PVT__wReg) 
                                             << 0x0000000dU)))));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut 
                    = ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        ^ vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32) 
                       >> 0x1fU);
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                    = (0x0000ffffffffffffULL & ((0x0000000000800000ULL 
                                                 | (QData)((IData)(
                                                                   (0x007fffffU 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                                * (0x0000000000800000ULL 
                                                   | (QData)((IData)(
                                                                     (0x007fffffU 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2 
                    = (0x000001ffU & (((0x000000ffU 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                           >> 0x17U)) 
                                       + (0x000000ffU 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                             >> 0x17U))) 
                                      + (1U & (IData)(
                                                      (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                       >> 0x2fU)))));
                if ((1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                   >> 0x2fU)))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x18U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x17U)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x17U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x16U)));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1 
                    = (2U == (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN 
                    = (3U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0 
                    = (1U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                    = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                        | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? ((((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5) 
                                               >> 0x0fU)))) 
                             << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5)) 
                           * (((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5) 
                                                 >> 0x0fU)))) 
                               << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5)))
                        : ((1U & (((~ ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) 
                                       | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1))) 
                                   | (IData)((0U == 
                                              (0x7fffffffU 
                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                  | (IData)((0U == 
                                             (0x7fffffffU 
                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32)))))
                            ? 0U : (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                     << 0x0000001fU) 
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2) 
                                                - (IData)(0x7fU)))
                                          : 0U) << 0x00000017U) 
                                       | (0x007fffffU 
                                          & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                              & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                  ? 
                                                 ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))
                                                  : 
                                                 (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                   | (~ (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut))) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))))
                                              ? ((IData)(1U) 
                                                 + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm)
                                              : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T 
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_4.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_4.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_4.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_4.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_4.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_4.__PVT__psumReg 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                    = (0x03ffffffU & ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                    = (0x07ffffffU & (((0x04000000U 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                                           << 1U)) 
                                       | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed) 
                                      + ((0x04000000U 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                                             << 1U)) 
                                         | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                    = (0x03ffffffU & ((0x04000000U 
                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       ? (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero 
                    = ((0x02000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                        ? 0U : ((0x01000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                 ? 1U : ((0x00800000U 
                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                          ? 2U : ((0x00400000U 
                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 3U
                                                   : 
                                                  ((0x00200000U 
                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                    ? 4U
                                                    : 
                                                   ((0x00100000U 
                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                     ? 5U
                                                     : 
                                                    ((0x00080000U 
                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                      ? 6U
                                                      : 
                                                     ((0x00040000U 
                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                       ? 7U
                                                       : 
                                                      ((0x00020000U 
                                                        & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                        ? 8U
                                                        : 
                                                       ((0x00010000U 
                                                         & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                         ? 9U
                                                         : 
                                                        ((0x00008000U 
                                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                          ? 0x0aU
                                                          : 
                                                         ((0x00004000U 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                           ? 0x0bU
                                                           : 
                                                          ((0x00002000U 
                                                            & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                            ? 0x0cU
                                                            : 
                                                           ((0x00001000U 
                                                             & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                             ? 0x0dU
                                                             : 
                                                            ((0x00000800U 
                                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                              ? 0x0eU
                                                              : 
                                                             ((0x00000400U 
                                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                               ? 0x0fU
                                                               : 
                                                              ((0x00000200U 
                                                                & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                ? 0x10U
                                                                : 
                                                               ((0x00000100U 
                                                                 & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                 ? 0x11U
                                                                 : 
                                                                ((0x00000080U 
                                                                  & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                  ? 0x12U
                                                                  : 
                                                                 ((0x00000040U 
                                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                   ? 0x13U
                                                                   : 
                                                                  ((0x00000020U 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                    ? 0x14U
                                                                    : 
                                                                   ((0x00000010U 
                                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                     ? 0x15U
                                                                     : 
                                                                    ((8U 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                      ? 0x16U
                                                                      : 
                                                                     ((4U 
                                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                       ? 0x17U
                                                                       : 
                                                                      (0x00000018U 
                                                                       | (1U 
                                                                          & (~ 
                                                                             (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                                                                              >> 1U))))))))))))))))))))))))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                    = (0x01ffffffffffffffULL & ((QData)((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)) 
                                                << (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3 
                    = (0x000000ffU & ((IData)(1U) + 
                                      ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                        ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_4.__PVT__psumReg 
                                           >> 0x17U)
                                        : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                           >> 0x17U))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_4.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_4.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_4.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                            - (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1)))
                                                         : 0U) 
                                                       << 0x00000017U) 
                                                      | (0x007fffffU 
                                                         & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                                 ? 
                                                                ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                  >> 0x1aU) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))
                                                                 : 
                                                                (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                                  | (~ 
                                                                     (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                      >> 0x1aU))) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))))
                                                             ? 
                                                            ((IData)(1U) 
                                                             + (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U)))
                                                             : (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U))))))))));
            }
        }
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_4.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__io_validOut_REG));
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__psumReg = 0U;
        vlSelfRef.__PVT__wReg = 0U;
    } else {
        if (vlSymsp->TOP.svsimTestbench__DOT__io_clear) {
            vlSelfRef.__PVT__psumReg = 0U;
        } else if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_5.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__io_aOut_REG) 
                                             << 0x0000000dU)))));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.__PVT__wReg) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.__PVT__wReg))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.__PVT__wReg) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.__PVT__wReg) 
                                             << 0x0000000dU)))));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut 
                    = ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        ^ vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32) 
                       >> 0x1fU);
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                    = (0x0000ffffffffffffULL & ((0x0000000000800000ULL 
                                                 | (QData)((IData)(
                                                                   (0x007fffffU 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                                * (0x0000000000800000ULL 
                                                   | (QData)((IData)(
                                                                     (0x007fffffU 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2 
                    = (0x000001ffU & (((0x000000ffU 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                           >> 0x17U)) 
                                       + (0x000000ffU 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                             >> 0x17U))) 
                                      + (1U & (IData)(
                                                      (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                       >> 0x2fU)))));
                if ((1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                   >> 0x2fU)))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x18U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x17U)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x17U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x16U)));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1 
                    = (2U == (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN 
                    = (3U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0 
                    = (1U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                    = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                        | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? ((((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5) 
                                               >> 0x0fU)))) 
                             << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5)) 
                           * (((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5) 
                                                 >> 0x0fU)))) 
                               << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5)))
                        : ((1U & (((~ ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) 
                                       | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1))) 
                                   | (IData)((0U == 
                                              (0x7fffffffU 
                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                  | (IData)((0U == 
                                             (0x7fffffffU 
                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32)))))
                            ? 0U : (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                     << 0x0000001fU) 
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2) 
                                                - (IData)(0x7fU)))
                                          : 0U) << 0x00000017U) 
                                       | (0x007fffffU 
                                          & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                              & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                  ? 
                                                 ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))
                                                  : 
                                                 (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                   | (~ (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut))) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))))
                                              ? ((IData)(1U) 
                                                 + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm)
                                              : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T 
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_5.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_5.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_5.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_5.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_5.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_5.__PVT__psumReg 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                    = (0x03ffffffU & ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                    = (0x07ffffffU & (((0x04000000U 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                                           << 1U)) 
                                       | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed) 
                                      + ((0x04000000U 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                                             << 1U)) 
                                         | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                    = (0x03ffffffU & ((0x04000000U 
                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       ? (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero 
                    = ((0x02000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                        ? 0U : ((0x01000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                 ? 1U : ((0x00800000U 
                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                          ? 2U : ((0x00400000U 
                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 3U
                                                   : 
                                                  ((0x00200000U 
                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                    ? 4U
                                                    : 
                                                   ((0x00100000U 
                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                     ? 5U
                                                     : 
                                                    ((0x00080000U 
                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                      ? 6U
                                                      : 
                                                     ((0x00040000U 
                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                       ? 7U
                                                       : 
                                                      ((0x00020000U 
                                                        & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                        ? 8U
                                                        : 
                                                       ((0x00010000U 
                                                         & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                         ? 9U
                                                         : 
                                                        ((0x00008000U 
                                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                          ? 0x0aU
                                                          : 
                                                         ((0x00004000U 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                           ? 0x0bU
                                                           : 
                                                          ((0x00002000U 
                                                            & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                            ? 0x0cU
                                                            : 
                                                           ((0x00001000U 
                                                             & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                             ? 0x0dU
                                                             : 
                                                            ((0x00000800U 
                                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                              ? 0x0eU
                                                              : 
                                                             ((0x00000400U 
                                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                               ? 0x0fU
                                                               : 
                                                              ((0x00000200U 
                                                                & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                ? 0x10U
                                                                : 
                                                               ((0x00000100U 
                                                                 & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                 ? 0x11U
                                                                 : 
                                                                ((0x00000080U 
                                                                  & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                  ? 0x12U
                                                                  : 
                                                                 ((0x00000040U 
                                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                   ? 0x13U
                                                                   : 
                                                                  ((0x00000020U 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                    ? 0x14U
                                                                    : 
                                                                   ((0x00000010U 
                                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                     ? 0x15U
                                                                     : 
                                                                    ((8U 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                      ? 0x16U
                                                                      : 
                                                                     ((4U 
                                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                       ? 0x17U
                                                                       : 
                                                                      (0x00000018U 
                                                                       | (1U 
                                                                          & (~ 
                                                                             (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                                                                              >> 1U))))))))))))))))))))))))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                    = (0x01ffffffffffffffULL & ((QData)((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)) 
                                                << (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3 
                    = (0x000000ffU & ((IData)(1U) + 
                                      ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                        ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_5.__PVT__psumReg 
                                           >> 0x17U)
                                        : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                           >> 0x17U))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_5.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_5.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_5.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                            - (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1)))
                                                         : 0U) 
                                                       << 0x00000017U) 
                                                      | (0x007fffffU 
                                                         & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                                 ? 
                                                                ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                  >> 0x1aU) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))
                                                                 : 
                                                                (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                                  | (~ 
                                                                     (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                      >> 0x1aU))) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))))
                                                             ? 
                                                            ((IData)(1U) 
                                                             + (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U)))
                                                             : (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U))))))))));
            }
        }
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_5.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__io_validOut_REG));
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__psumReg = 0U;
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
        vlSelfRef.__PVT__wReg = 0U;
    } else {
        if (vlSymsp->TOP.svsimTestbench__DOT__io_clear) {
            vlSelfRef.__PVT__psumReg = 0U;
        } else if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_6.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__io_aOut_REG) 
                                             << 0x0000000dU)))));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.__PVT__wReg) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.__PVT__wReg))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.__PVT__wReg) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.__PVT__wReg) 
                                             << 0x0000000dU)))));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut 
                    = ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        ^ vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32) 
                       >> 0x1fU);
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                    = (0x0000ffffffffffffULL & ((0x0000000000800000ULL 
                                                 | (QData)((IData)(
                                                                   (0x007fffffU 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                                * (0x0000000000800000ULL 
                                                   | (QData)((IData)(
                                                                     (0x007fffffU 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2 
                    = (0x000001ffU & (((0x000000ffU 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                           >> 0x17U)) 
                                       + (0x000000ffU 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                             >> 0x17U))) 
                                      + (1U & (IData)(
                                                      (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                       >> 0x2fU)))));
                if ((1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                   >> 0x2fU)))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x18U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x17U)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x17U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x16U)));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1 
                    = (2U == (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN 
                    = (3U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0 
                    = (1U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                    = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                        | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? ((((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5) 
                                               >> 0x0fU)))) 
                             << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5)) 
                           * (((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5) 
                                                 >> 0x0fU)))) 
                               << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5)))
                        : ((1U & (((~ ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) 
                                       | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1))) 
                                   | (IData)((0U == 
                                              (0x7fffffffU 
                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                  | (IData)((0U == 
                                             (0x7fffffffU 
                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32)))))
                            ? 0U : (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                     << 0x0000001fU) 
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2) 
                                                - (IData)(0x7fU)))
                                          : 0U) << 0x00000017U) 
                                       | (0x007fffffU 
                                          & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                              & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                  ? 
                                                 ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))
                                                  : 
                                                 (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                   | (~ (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut))) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))))
                                              ? ((IData)(1U) 
                                                 + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm)
                                              : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T 
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_6.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_6.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_6.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_6.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_6.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_6.__PVT__psumReg 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                    = (0x03ffffffU & ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                    = (0x07ffffffU & (((0x04000000U 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                                           << 1U)) 
                                       | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed) 
                                      + ((0x04000000U 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                                             << 1U)) 
                                         | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                    = (0x03ffffffU & ((0x04000000U 
                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       ? (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero 
                    = ((0x02000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                        ? 0U : ((0x01000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                 ? 1U : ((0x00800000U 
                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                          ? 2U : ((0x00400000U 
                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 3U
                                                   : 
                                                  ((0x00200000U 
                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                    ? 4U
                                                    : 
                                                   ((0x00100000U 
                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                     ? 5U
                                                     : 
                                                    ((0x00080000U 
                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                      ? 6U
                                                      : 
                                                     ((0x00040000U 
                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                       ? 7U
                                                       : 
                                                      ((0x00020000U 
                                                        & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                        ? 8U
                                                        : 
                                                       ((0x00010000U 
                                                         & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                         ? 9U
                                                         : 
                                                        ((0x00008000U 
                                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                          ? 0x0aU
                                                          : 
                                                         ((0x00004000U 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                           ? 0x0bU
                                                           : 
                                                          ((0x00002000U 
                                                            & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                            ? 0x0cU
                                                            : 
                                                           ((0x00001000U 
                                                             & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                             ? 0x0dU
                                                             : 
                                                            ((0x00000800U 
                                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                              ? 0x0eU
                                                              : 
                                                             ((0x00000400U 
                                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                               ? 0x0fU
                                                               : 
                                                              ((0x00000200U 
                                                                & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                ? 0x10U
                                                                : 
                                                               ((0x00000100U 
                                                                 & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                 ? 0x11U
                                                                 : 
                                                                ((0x00000080U 
                                                                  & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                  ? 0x12U
                                                                  : 
                                                                 ((0x00000040U 
                                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                   ? 0x13U
                                                                   : 
                                                                  ((0x00000020U 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                    ? 0x14U
                                                                    : 
                                                                   ((0x00000010U 
                                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                     ? 0x15U
                                                                     : 
                                                                    ((8U 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                      ? 0x16U
                                                                      : 
                                                                     ((4U 
                                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                       ? 0x17U
                                                                       : 
                                                                      (0x00000018U 
                                                                       | (1U 
                                                                          & (~ 
                                                                             (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                                                                              >> 1U))))))))))))))))))))))))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                    = (0x01ffffffffffffffULL & ((QData)((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)) 
                                                << (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3 
                    = (0x000000ffU & ((IData)(1U) + 
                                      ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                        ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_6.__PVT__psumReg 
                                           >> 0x17U)
                                        : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                           >> 0x17U))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_6.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_6.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_6.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                            - (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1)))
                                                         : 0U) 
                                                       << 0x00000017U) 
                                                      | (0x007fffffU 
                                                         & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                                 ? 
                                                                ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                  >> 0x1aU) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))
                                                                 : 
                                                                (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                                  | (~ 
                                                                     (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                      >> 0x1aU))) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))))
                                                             ? 
                                                            ((IData)(1U) 
                                                             + (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U)))
                                                             : (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U))))))))));
            }
        }
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__io_rndOut_REG;
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_6.__PVT__psumReg);
        }
    }
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__io_validOut_REG));
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_7__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_7__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__io_validOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_7__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_7__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__psumReg = 0U;
        vlSelfRef.__PVT__wReg = 0U;
    } else {
        if (vlSymsp->TOP.svsimTestbench__DOT__io_clear) {
            vlSelfRef.__PVT__psumReg = 0U;
        } else if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_7.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__io_aOut_REG) 
                                             << 0x0000000dU)))));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.__PVT__wReg) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.__PVT__wReg))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.__PVT__wReg) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.__PVT__wReg) 
                                             << 0x0000000dU)))));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut 
                    = ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        ^ vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32) 
                       >> 0x1fU);
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                    = (0x0000ffffffffffffULL & ((0x0000000000800000ULL 
                                                 | (QData)((IData)(
                                                                   (0x007fffffU 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                                * (0x0000000000800000ULL 
                                                   | (QData)((IData)(
                                                                     (0x007fffffU 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2 
                    = (0x000001ffU & (((0x000000ffU 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                           >> 0x17U)) 
                                       + (0x000000ffU 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                             >> 0x17U))) 
                                      + (1U & (IData)(
                                                      (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                       >> 0x2fU)))));
                if ((1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                   >> 0x2fU)))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x18U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x17U)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x17U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x16U)));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1 
                    = (2U == (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN 
                    = (3U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0 
                    = (1U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                    = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                        | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? ((((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5) 
                                               >> 0x0fU)))) 
                             << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5)) 
                           * (((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5) 
                                                 >> 0x0fU)))) 
                               << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5)))
                        : ((1U & (((~ ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) 
                                       | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1))) 
                                   | (IData)((0U == 
                                              (0x7fffffffU 
                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                  | (IData)((0U == 
                                             (0x7fffffffU 
                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32)))))
                            ? 0U : (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                     << 0x0000001fU) 
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2) 
                                                - (IData)(0x7fU)))
                                          : 0U) << 0x00000017U) 
                                       | (0x007fffffU 
                                          & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                              & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                  ? 
                                                 ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))
                                                  : 
                                                 (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                   | (~ (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut))) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))))
                                              ? ((IData)(1U) 
                                                 + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm)
                                              : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T 
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_7.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_7.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_7.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_7.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_7.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_7.__PVT__psumReg 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                    = (0x03ffffffU & ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                    = (0x07ffffffU & (((0x04000000U 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                                           << 1U)) 
                                       | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed) 
                                      + ((0x04000000U 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                                             << 1U)) 
                                         | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                    = (0x03ffffffU & ((0x04000000U 
                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       ? (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero 
                    = ((0x02000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                        ? 0U : ((0x01000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                 ? 1U : ((0x00800000U 
                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                          ? 2U : ((0x00400000U 
                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 3U
                                                   : 
                                                  ((0x00200000U 
                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                    ? 4U
                                                    : 
                                                   ((0x00100000U 
                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                     ? 5U
                                                     : 
                                                    ((0x00080000U 
                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                      ? 6U
                                                      : 
                                                     ((0x00040000U 
                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                       ? 7U
                                                       : 
                                                      ((0x00020000U 
                                                        & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                        ? 8U
                                                        : 
                                                       ((0x00010000U 
                                                         & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                         ? 9U
                                                         : 
                                                        ((0x00008000U 
                                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                          ? 0x0aU
                                                          : 
                                                         ((0x00004000U 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                           ? 0x0bU
                                                           : 
                                                          ((0x00002000U 
                                                            & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                            ? 0x0cU
                                                            : 
                                                           ((0x00001000U 
                                                             & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                             ? 0x0dU
                                                             : 
                                                            ((0x00000800U 
                                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                              ? 0x0eU
                                                              : 
                                                             ((0x00000400U 
                                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                               ? 0x0fU
                                                               : 
                                                              ((0x00000200U 
                                                                & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                ? 0x10U
                                                                : 
                                                               ((0x00000100U 
                                                                 & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                 ? 0x11U
                                                                 : 
                                                                ((0x00000080U 
                                                                  & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                  ? 0x12U
                                                                  : 
                                                                 ((0x00000040U 
                                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                   ? 0x13U
                                                                   : 
                                                                  ((0x00000020U 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                    ? 0x14U
                                                                    : 
                                                                   ((0x00000010U 
                                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                     ? 0x15U
                                                                     : 
                                                                    ((8U 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                      ? 0x16U
                                                                      : 
                                                                     ((4U 
                                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                       ? 0x17U
                                                                       : 
                                                                      (0x00000018U 
                                                                       | (1U 
                                                                          & (~ 
                                                                             (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                                                                              >> 1U))))))))))))))))))))))))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                    = (0x01ffffffffffffffULL & ((QData)((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)) 
                                                << (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3 
                    = (0x000000ffU & ((IData)(1U) + 
                                      ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                        ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_7.__PVT__psumReg 
                                           >> 0x17U)
                                        : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                           >> 0x17U))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_7.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_7.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_7.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                            - (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1)))
                                                         : 0U) 
                                                       << 0x00000017U) 
                                                      | (0x007fffffU 
                                                         & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                                 ? 
                                                                ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                  >> 0x1aU) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))
                                                                 : 
                                                                (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                                  | (~ 
                                                                     (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                      >> 0x1aU))) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))))
                                                             ? 
                                                            ((IData)(1U) 
                                                             + (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U)))
                                                             : (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U))))))))));
            }
        }
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_1_7.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_7__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_7__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__psumReg = 0U;
        vlSelfRef.__PVT__wReg = 0U;
    } else {
        if (vlSymsp->TOP.svsimTestbench__DOT__io_clear) {
            vlSelfRef.__PVT__psumReg = 0U;
        } else if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_11) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_11) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_11)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP.svsimTestbench__DOT__io_aIn_11;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_11) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_11) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_11))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_11) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_11) 
                                             << 0x0000000dU)))));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.__PVT__wReg) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.__PVT__wReg))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.__PVT__wReg) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.__PVT__wReg) 
                                             << 0x0000000dU)))));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut 
                    = ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        ^ vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32) 
                       >> 0x1fU);
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                    = (0x0000ffffffffffffULL & ((0x0000000000800000ULL 
                                                 | (QData)((IData)(
                                                                   (0x007fffffU 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                                * (0x0000000000800000ULL 
                                                   | (QData)((IData)(
                                                                     (0x007fffffU 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2 
                    = (0x000001ffU & (((0x000000ffU 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                           >> 0x17U)) 
                                       + (0x000000ffU 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                             >> 0x17U))) 
                                      + (1U & (IData)(
                                                      (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                       >> 0x2fU)))));
                if ((1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                   >> 0x2fU)))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x18U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x17U)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x17U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x16U)));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1 
                    = (2U == (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN 
                    = (3U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0 
                    = (1U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                    = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                        | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? ((((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5) 
                                               >> 0x0fU)))) 
                             << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5)) 
                           * (((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5) 
                                                 >> 0x0fU)))) 
                               << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5)))
                        : ((1U & (((~ ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) 
                                       | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1))) 
                                   | (IData)((0U == 
                                              (0x7fffffffU 
                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                  | (IData)((0U == 
                                             (0x7fffffffU 
                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32)))))
                            ? 0U : (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                     << 0x0000001fU) 
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2) 
                                                - (IData)(0x7fU)))
                                          : 0U) << 0x00000017U) 
                                       | (0x007fffffU 
                                          & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                              & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                  ? 
                                                 ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))
                                                  : 
                                                 (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                   | (~ (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut))) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))))
                                              ? ((IData)(1U) 
                                                 + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm)
                                              : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T 
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__psumReg 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                    = (0x03ffffffU & ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                    = (0x07ffffffU & (((0x04000000U 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                                           << 1U)) 
                                       | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed) 
                                      + ((0x04000000U 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                                             << 1U)) 
                                         | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                    = (0x03ffffffU & ((0x04000000U 
                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       ? (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero 
                    = ((0x02000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                        ? 0U : ((0x01000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                 ? 1U : ((0x00800000U 
                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                          ? 2U : ((0x00400000U 
                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 3U
                                                   : 
                                                  ((0x00200000U 
                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                    ? 4U
                                                    : 
                                                   ((0x00100000U 
                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                     ? 5U
                                                     : 
                                                    ((0x00080000U 
                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                      ? 6U
                                                      : 
                                                     ((0x00040000U 
                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                       ? 7U
                                                       : 
                                                      ((0x00020000U 
                                                        & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                        ? 8U
                                                        : 
                                                       ((0x00010000U 
                                                         & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                         ? 9U
                                                         : 
                                                        ((0x00008000U 
                                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                          ? 0x0aU
                                                          : 
                                                         ((0x00004000U 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                           ? 0x0bU
                                                           : 
                                                          ((0x00002000U 
                                                            & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                            ? 0x0cU
                                                            : 
                                                           ((0x00001000U 
                                                             & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                             ? 0x0dU
                                                             : 
                                                            ((0x00000800U 
                                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                              ? 0x0eU
                                                              : 
                                                             ((0x00000400U 
                                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                               ? 0x0fU
                                                               : 
                                                              ((0x00000200U 
                                                                & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                ? 0x10U
                                                                : 
                                                               ((0x00000100U 
                                                                 & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                 ? 0x11U
                                                                 : 
                                                                ((0x00000080U 
                                                                  & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                  ? 0x12U
                                                                  : 
                                                                 ((0x00000040U 
                                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                   ? 0x13U
                                                                   : 
                                                                  ((0x00000020U 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                    ? 0x14U
                                                                    : 
                                                                   ((0x00000010U 
                                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                     ? 0x15U
                                                                     : 
                                                                    ((8U 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                      ? 0x16U
                                                                      : 
                                                                     ((4U 
                                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                       ? 0x17U
                                                                       : 
                                                                      (0x00000018U 
                                                                       | (1U 
                                                                          & (~ 
                                                                             (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                                                                              >> 1U))))))))))))))))))))))))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                    = (0x01ffffffffffffffULL & ((QData)((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)) 
                                                << (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3 
                    = (0x000000ffU & ((IData)(1U) + 
                                      ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                        ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__psumReg 
                                           >> 0x17U)
                                        : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                           >> 0x17U))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                            - (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1)))
                                                         : 0U) 
                                                       << 0x00000017U) 
                                                      | (0x007fffffU 
                                                         & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                                 ? 
                                                                ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                  >> 0x1aU) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))
                                                                 : 
                                                                (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                                  | (~ 
                                                                     (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                      >> 0x1aU))) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))))
                                                             ? 
                                                            ((IData)(1U) 
                                                             + (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U)))
                                                             : (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U))))))))));
            }
        }
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP.svsimTestbench__DOT__io_fmtIn;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP.svsimTestbench__DOT__io_rndIn;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_0.__PVT__io_validOut_REG));
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP.svsimTestbench__DOT__io_fmtIn;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP.svsimTestbench__DOT__io_rndIn;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_11));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__psumReg = 0U;
        vlSelfRef.__PVT__wReg = 0U;
    } else {
        if (vlSymsp->TOP.svsimTestbench__DOT__io_clear) {
            vlSelfRef.__PVT__psumReg = 0U;
        } else if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__io_aOut_REG) 
                                             << 0x0000000dU)))));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.__PVT__wReg) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.__PVT__wReg))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.__PVT__wReg) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.__PVT__wReg) 
                                             << 0x0000000dU)))));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut 
                    = ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        ^ vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32) 
                       >> 0x1fU);
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                    = (0x0000ffffffffffffULL & ((0x0000000000800000ULL 
                                                 | (QData)((IData)(
                                                                   (0x007fffffU 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                                * (0x0000000000800000ULL 
                                                   | (QData)((IData)(
                                                                     (0x007fffffU 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2 
                    = (0x000001ffU & (((0x000000ffU 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                           >> 0x17U)) 
                                       + (0x000000ffU 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                             >> 0x17U))) 
                                      + (1U & (IData)(
                                                      (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                       >> 0x2fU)))));
                if ((1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                   >> 0x2fU)))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x18U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x17U)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x17U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x16U)));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1 
                    = (2U == (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN 
                    = (3U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0 
                    = (1U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                    = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                        | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? ((((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5) 
                                               >> 0x0fU)))) 
                             << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5)) 
                           * (((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5) 
                                                 >> 0x0fU)))) 
                               << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5)))
                        : ((1U & (((~ ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) 
                                       | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1))) 
                                   | (IData)((0U == 
                                              (0x7fffffffU 
                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                  | (IData)((0U == 
                                             (0x7fffffffU 
                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32)))))
                            ? 0U : (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                     << 0x0000001fU) 
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2) 
                                                - (IData)(0x7fU)))
                                          : 0U) << 0x00000017U) 
                                       | (0x007fffffU 
                                          & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                              & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                  ? 
                                                 ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))
                                                  : 
                                                 (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                   | (~ (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut))) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))))
                                              ? ((IData)(1U) 
                                                 + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm)
                                              : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T 
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__psumReg 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                    = (0x03ffffffU & ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                    = (0x07ffffffU & (((0x04000000U 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                                           << 1U)) 
                                       | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed) 
                                      + ((0x04000000U 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                                             << 1U)) 
                                         | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                    = (0x03ffffffU & ((0x04000000U 
                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       ? (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero 
                    = ((0x02000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                        ? 0U : ((0x01000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                 ? 1U : ((0x00800000U 
                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                          ? 2U : ((0x00400000U 
                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 3U
                                                   : 
                                                  ((0x00200000U 
                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                    ? 4U
                                                    : 
                                                   ((0x00100000U 
                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                     ? 5U
                                                     : 
                                                    ((0x00080000U 
                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                      ? 6U
                                                      : 
                                                     ((0x00040000U 
                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                       ? 7U
                                                       : 
                                                      ((0x00020000U 
                                                        & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                        ? 8U
                                                        : 
                                                       ((0x00010000U 
                                                         & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                         ? 9U
                                                         : 
                                                        ((0x00008000U 
                                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                          ? 0x0aU
                                                          : 
                                                         ((0x00004000U 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                           ? 0x0bU
                                                           : 
                                                          ((0x00002000U 
                                                            & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                            ? 0x0cU
                                                            : 
                                                           ((0x00001000U 
                                                             & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                             ? 0x0dU
                                                             : 
                                                            ((0x00000800U 
                                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                              ? 0x0eU
                                                              : 
                                                             ((0x00000400U 
                                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                               ? 0x0fU
                                                               : 
                                                              ((0x00000200U 
                                                                & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                ? 0x10U
                                                                : 
                                                               ((0x00000100U 
                                                                 & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                 ? 0x11U
                                                                 : 
                                                                ((0x00000080U 
                                                                  & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                  ? 0x12U
                                                                  : 
                                                                 ((0x00000040U 
                                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                   ? 0x13U
                                                                   : 
                                                                  ((0x00000020U 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                    ? 0x14U
                                                                    : 
                                                                   ((0x00000010U 
                                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                     ? 0x15U
                                                                     : 
                                                                    ((8U 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                      ? 0x16U
                                                                      : 
                                                                     ((4U 
                                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                       ? 0x17U
                                                                       : 
                                                                      (0x00000018U 
                                                                       | (1U 
                                                                          & (~ 
                                                                             (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                                                                              >> 1U))))))))))))))))))))))))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                    = (0x01ffffffffffffffULL & ((QData)((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)) 
                                                << (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3 
                    = (0x000000ffU & ((IData)(1U) + 
                                      ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                        ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__psumReg 
                                           >> 0x17U)
                                        : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                           >> 0x17U))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                            - (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1)))
                                                         : 0U) 
                                                       << 0x00000017U) 
                                                      | (0x007fffffU 
                                                         & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                                 ? 
                                                                ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                  >> 0x1aU) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))
                                                                 : 
                                                                (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                                  | (~ 
                                                                     (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                      >> 0x1aU))) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))))
                                                             ? 
                                                            ((IData)(1U) 
                                                             + (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U)))
                                                             : (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U))))))))));
            }
        }
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_1.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__io_validOut_REG));
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__psumReg = 0U;
        vlSelfRef.__PVT__wReg = 0U;
    } else {
        if (vlSymsp->TOP.svsimTestbench__DOT__io_clear) {
            vlSelfRef.__PVT__psumReg = 0U;
        } else if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__io_aOut_REG) 
                                             << 0x0000000dU)))));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.__PVT__wReg) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.__PVT__wReg))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.__PVT__wReg) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.__PVT__wReg) 
                                             << 0x0000000dU)))));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut 
                    = ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        ^ vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32) 
                       >> 0x1fU);
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                    = (0x0000ffffffffffffULL & ((0x0000000000800000ULL 
                                                 | (QData)((IData)(
                                                                   (0x007fffffU 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                                * (0x0000000000800000ULL 
                                                   | (QData)((IData)(
                                                                     (0x007fffffU 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2 
                    = (0x000001ffU & (((0x000000ffU 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                           >> 0x17U)) 
                                       + (0x000000ffU 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                             >> 0x17U))) 
                                      + (1U & (IData)(
                                                      (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                       >> 0x2fU)))));
                if ((1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                   >> 0x2fU)))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x18U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x17U)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x17U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x16U)));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1 
                    = (2U == (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN 
                    = (3U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0 
                    = (1U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                    = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                        | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? ((((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5) 
                                               >> 0x0fU)))) 
                             << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5)) 
                           * (((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5) 
                                                 >> 0x0fU)))) 
                               << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5)))
                        : ((1U & (((~ ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) 
                                       | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1))) 
                                   | (IData)((0U == 
                                              (0x7fffffffU 
                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                  | (IData)((0U == 
                                             (0x7fffffffU 
                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32)))))
                            ? 0U : (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                     << 0x0000001fU) 
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2) 
                                                - (IData)(0x7fU)))
                                          : 0U) << 0x00000017U) 
                                       | (0x007fffffU 
                                          & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                              & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                  ? 
                                                 ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))
                                                  : 
                                                 (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                   | (~ (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut))) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))))
                                              ? ((IData)(1U) 
                                                 + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm)
                                              : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T 
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__psumReg 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                    = (0x03ffffffU & ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                    = (0x07ffffffU & (((0x04000000U 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                                           << 1U)) 
                                       | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed) 
                                      + ((0x04000000U 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                                             << 1U)) 
                                         | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                    = (0x03ffffffU & ((0x04000000U 
                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       ? (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero 
                    = ((0x02000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                        ? 0U : ((0x01000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                 ? 1U : ((0x00800000U 
                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                          ? 2U : ((0x00400000U 
                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 3U
                                                   : 
                                                  ((0x00200000U 
                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                    ? 4U
                                                    : 
                                                   ((0x00100000U 
                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                     ? 5U
                                                     : 
                                                    ((0x00080000U 
                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                      ? 6U
                                                      : 
                                                     ((0x00040000U 
                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                       ? 7U
                                                       : 
                                                      ((0x00020000U 
                                                        & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                        ? 8U
                                                        : 
                                                       ((0x00010000U 
                                                         & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                         ? 9U
                                                         : 
                                                        ((0x00008000U 
                                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                          ? 0x0aU
                                                          : 
                                                         ((0x00004000U 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                           ? 0x0bU
                                                           : 
                                                          ((0x00002000U 
                                                            & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                            ? 0x0cU
                                                            : 
                                                           ((0x00001000U 
                                                             & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                             ? 0x0dU
                                                             : 
                                                            ((0x00000800U 
                                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                              ? 0x0eU
                                                              : 
                                                             ((0x00000400U 
                                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                               ? 0x0fU
                                                               : 
                                                              ((0x00000200U 
                                                                & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                ? 0x10U
                                                                : 
                                                               ((0x00000100U 
                                                                 & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                 ? 0x11U
                                                                 : 
                                                                ((0x00000080U 
                                                                  & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                  ? 0x12U
                                                                  : 
                                                                 ((0x00000040U 
                                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                   ? 0x13U
                                                                   : 
                                                                  ((0x00000020U 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                    ? 0x14U
                                                                    : 
                                                                   ((0x00000010U 
                                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                     ? 0x15U
                                                                     : 
                                                                    ((8U 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                      ? 0x16U
                                                                      : 
                                                                     ((4U 
                                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                       ? 0x17U
                                                                       : 
                                                                      (0x00000018U 
                                                                       | (1U 
                                                                          & (~ 
                                                                             (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                                                                              >> 1U))))))))))))))))))))))))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                    = (0x01ffffffffffffffULL & ((QData)((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)) 
                                                << (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3 
                    = (0x000000ffU & ((IData)(1U) + 
                                      ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                        ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__psumReg 
                                           >> 0x17U)
                                        : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                           >> 0x17U))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                            - (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1)))
                                                         : 0U) 
                                                       << 0x00000017U) 
                                                      | (0x007fffffU 
                                                         & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                                 ? 
                                                                ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                  >> 0x1aU) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))
                                                                 : 
                                                                (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                                  | (~ 
                                                                     (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                      >> 0x1aU))) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))))
                                                             ? 
                                                            ((IData)(1U) 
                                                             + (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U)))
                                                             : (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U))))))))));
            }
        }
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_2.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__io_validOut_REG));
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__psumReg = 0U;
        vlSelfRef.__PVT__wReg = 0U;
    } else {
        if (vlSymsp->TOP.svsimTestbench__DOT__io_clear) {
            vlSelfRef.__PVT__psumReg = 0U;
        } else if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__io_aOut_REG) 
                                             << 0x0000000dU)))));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.__PVT__wReg) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.__PVT__wReg))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.__PVT__wReg) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.__PVT__wReg) 
                                             << 0x0000000dU)))));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut 
                    = ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        ^ vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32) 
                       >> 0x1fU);
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                    = (0x0000ffffffffffffULL & ((0x0000000000800000ULL 
                                                 | (QData)((IData)(
                                                                   (0x007fffffU 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                                * (0x0000000000800000ULL 
                                                   | (QData)((IData)(
                                                                     (0x007fffffU 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2 
                    = (0x000001ffU & (((0x000000ffU 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                           >> 0x17U)) 
                                       + (0x000000ffU 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                             >> 0x17U))) 
                                      + (1U & (IData)(
                                                      (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                       >> 0x2fU)))));
                if ((1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                   >> 0x2fU)))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x18U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x17U)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x17U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x16U)));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1 
                    = (2U == (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN 
                    = (3U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0 
                    = (1U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                    = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                        | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? ((((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5) 
                                               >> 0x0fU)))) 
                             << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5)) 
                           * (((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5) 
                                                 >> 0x0fU)))) 
                               << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5)))
                        : ((1U & (((~ ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) 
                                       | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1))) 
                                   | (IData)((0U == 
                                              (0x7fffffffU 
                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                  | (IData)((0U == 
                                             (0x7fffffffU 
                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32)))))
                            ? 0U : (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                     << 0x0000001fU) 
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2) 
                                                - (IData)(0x7fU)))
                                          : 0U) << 0x00000017U) 
                                       | (0x007fffffU 
                                          & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                              & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                  ? 
                                                 ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))
                                                  : 
                                                 (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                   | (~ (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut))) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))))
                                              ? ((IData)(1U) 
                                                 + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm)
                                              : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T 
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__psumReg 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                    = (0x03ffffffU & ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                    = (0x07ffffffU & (((0x04000000U 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                                           << 1U)) 
                                       | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed) 
                                      + ((0x04000000U 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                                             << 1U)) 
                                         | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                    = (0x03ffffffU & ((0x04000000U 
                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       ? (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero 
                    = ((0x02000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                        ? 0U : ((0x01000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                 ? 1U : ((0x00800000U 
                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                          ? 2U : ((0x00400000U 
                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 3U
                                                   : 
                                                  ((0x00200000U 
                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                    ? 4U
                                                    : 
                                                   ((0x00100000U 
                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                     ? 5U
                                                     : 
                                                    ((0x00080000U 
                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                      ? 6U
                                                      : 
                                                     ((0x00040000U 
                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                       ? 7U
                                                       : 
                                                      ((0x00020000U 
                                                        & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                        ? 8U
                                                        : 
                                                       ((0x00010000U 
                                                         & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                         ? 9U
                                                         : 
                                                        ((0x00008000U 
                                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                          ? 0x0aU
                                                          : 
                                                         ((0x00004000U 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                           ? 0x0bU
                                                           : 
                                                          ((0x00002000U 
                                                            & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                            ? 0x0cU
                                                            : 
                                                           ((0x00001000U 
                                                             & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                             ? 0x0dU
                                                             : 
                                                            ((0x00000800U 
                                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                              ? 0x0eU
                                                              : 
                                                             ((0x00000400U 
                                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                               ? 0x0fU
                                                               : 
                                                              ((0x00000200U 
                                                                & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                ? 0x10U
                                                                : 
                                                               ((0x00000100U 
                                                                 & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                 ? 0x11U
                                                                 : 
                                                                ((0x00000080U 
                                                                  & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                  ? 0x12U
                                                                  : 
                                                                 ((0x00000040U 
                                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                   ? 0x13U
                                                                   : 
                                                                  ((0x00000020U 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                    ? 0x14U
                                                                    : 
                                                                   ((0x00000010U 
                                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                     ? 0x15U
                                                                     : 
                                                                    ((8U 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                      ? 0x16U
                                                                      : 
                                                                     ((4U 
                                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                       ? 0x17U
                                                                       : 
                                                                      (0x00000018U 
                                                                       | (1U 
                                                                          & (~ 
                                                                             (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                                                                              >> 1U))))))))))))))))))))))))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                    = (0x01ffffffffffffffULL & ((QData)((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)) 
                                                << (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3 
                    = (0x000000ffU & ((IData)(1U) + 
                                      ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                        ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__psumReg 
                                           >> 0x17U)
                                        : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                           >> 0x17U))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                            - (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1)))
                                                         : 0U) 
                                                       << 0x00000017U) 
                                                      | (0x007fffffU 
                                                         & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                                 ? 
                                                                ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                  >> 0x1aU) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))
                                                                 : 
                                                                (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                                  | (~ 
                                                                     (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                      >> 0x1aU))) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))))
                                                             ? 
                                                            ((IData)(1U) 
                                                             + (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U)))
                                                             : (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U))))))))));
            }
        }
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_3.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__io_validOut_REG));
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__psumReg = 0U;
        vlSelfRef.__PVT__wReg = 0U;
    } else {
        if (vlSymsp->TOP.svsimTestbench__DOT__io_clear) {
            vlSelfRef.__PVT__psumReg = 0U;
        } else if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__io_aOut_REG) 
                                             << 0x0000000dU)))));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.__PVT__wReg) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.__PVT__wReg))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.__PVT__wReg) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.__PVT__wReg) 
                                             << 0x0000000dU)))));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut 
                    = ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        ^ vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32) 
                       >> 0x1fU);
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                    = (0x0000ffffffffffffULL & ((0x0000000000800000ULL 
                                                 | (QData)((IData)(
                                                                   (0x007fffffU 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                                * (0x0000000000800000ULL 
                                                   | (QData)((IData)(
                                                                     (0x007fffffU 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2 
                    = (0x000001ffU & (((0x000000ffU 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                           >> 0x17U)) 
                                       + (0x000000ffU 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                             >> 0x17U))) 
                                      + (1U & (IData)(
                                                      (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                       >> 0x2fU)))));
                if ((1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                   >> 0x2fU)))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x18U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x17U)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x17U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x16U)));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1 
                    = (2U == (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN 
                    = (3U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0 
                    = (1U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                    = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                        | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? ((((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5) 
                                               >> 0x0fU)))) 
                             << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5)) 
                           * (((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5) 
                                                 >> 0x0fU)))) 
                               << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5)))
                        : ((1U & (((~ ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) 
                                       | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1))) 
                                   | (IData)((0U == 
                                              (0x7fffffffU 
                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                  | (IData)((0U == 
                                             (0x7fffffffU 
                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32)))))
                            ? 0U : (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                     << 0x0000001fU) 
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2) 
                                                - (IData)(0x7fU)))
                                          : 0U) << 0x00000017U) 
                                       | (0x007fffffU 
                                          & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                              & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                  ? 
                                                 ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))
                                                  : 
                                                 (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                   | (~ (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut))) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))))
                                              ? ((IData)(1U) 
                                                 + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm)
                                              : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T 
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__psumReg 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                    = (0x03ffffffU & ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                    = (0x07ffffffU & (((0x04000000U 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                                           << 1U)) 
                                       | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed) 
                                      + ((0x04000000U 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                                             << 1U)) 
                                         | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                    = (0x03ffffffU & ((0x04000000U 
                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       ? (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero 
                    = ((0x02000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                        ? 0U : ((0x01000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                 ? 1U : ((0x00800000U 
                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                          ? 2U : ((0x00400000U 
                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 3U
                                                   : 
                                                  ((0x00200000U 
                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                    ? 4U
                                                    : 
                                                   ((0x00100000U 
                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                     ? 5U
                                                     : 
                                                    ((0x00080000U 
                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                      ? 6U
                                                      : 
                                                     ((0x00040000U 
                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                       ? 7U
                                                       : 
                                                      ((0x00020000U 
                                                        & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                        ? 8U
                                                        : 
                                                       ((0x00010000U 
                                                         & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                         ? 9U
                                                         : 
                                                        ((0x00008000U 
                                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                          ? 0x0aU
                                                          : 
                                                         ((0x00004000U 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                           ? 0x0bU
                                                           : 
                                                          ((0x00002000U 
                                                            & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                            ? 0x0cU
                                                            : 
                                                           ((0x00001000U 
                                                             & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                             ? 0x0dU
                                                             : 
                                                            ((0x00000800U 
                                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                              ? 0x0eU
                                                              : 
                                                             ((0x00000400U 
                                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                               ? 0x0fU
                                                               : 
                                                              ((0x00000200U 
                                                                & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                ? 0x10U
                                                                : 
                                                               ((0x00000100U 
                                                                 & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                 ? 0x11U
                                                                 : 
                                                                ((0x00000080U 
                                                                  & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                  ? 0x12U
                                                                  : 
                                                                 ((0x00000040U 
                                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                   ? 0x13U
                                                                   : 
                                                                  ((0x00000020U 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                    ? 0x14U
                                                                    : 
                                                                   ((0x00000010U 
                                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                     ? 0x15U
                                                                     : 
                                                                    ((8U 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                      ? 0x16U
                                                                      : 
                                                                     ((4U 
                                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                       ? 0x17U
                                                                       : 
                                                                      (0x00000018U 
                                                                       | (1U 
                                                                          & (~ 
                                                                             (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                                                                              >> 1U))))))))))))))))))))))))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                    = (0x01ffffffffffffffULL & ((QData)((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)) 
                                                << (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3 
                    = (0x000000ffU & ((IData)(1U) + 
                                      ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                        ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__psumReg 
                                           >> 0x17U)
                                        : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                           >> 0x17U))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                            - (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1)))
                                                         : 0U) 
                                                       << 0x00000017U) 
                                                      | (0x007fffffU 
                                                         & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                                 ? 
                                                                ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                  >> 0x1aU) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))
                                                                 : 
                                                                (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                                  | (~ 
                                                                     (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                      >> 0x1aU))) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))))
                                                             ? 
                                                            ((IData)(1U) 
                                                             + (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U)))
                                                             : (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U))))))))));
            }
        }
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_4.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__io_validOut_REG));
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__psumReg = 0U;
        vlSelfRef.__PVT__wReg = 0U;
    } else {
        if (vlSymsp->TOP.svsimTestbench__DOT__io_clear) {
            vlSelfRef.__PVT__psumReg = 0U;
        } else if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__io_aOut_REG) 
                                             << 0x0000000dU)))));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.__PVT__wReg) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.__PVT__wReg))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.__PVT__wReg) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.__PVT__wReg) 
                                             << 0x0000000dU)))));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut 
                    = ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        ^ vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32) 
                       >> 0x1fU);
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                    = (0x0000ffffffffffffULL & ((0x0000000000800000ULL 
                                                 | (QData)((IData)(
                                                                   (0x007fffffU 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                                * (0x0000000000800000ULL 
                                                   | (QData)((IData)(
                                                                     (0x007fffffU 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2 
                    = (0x000001ffU & (((0x000000ffU 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                           >> 0x17U)) 
                                       + (0x000000ffU 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                             >> 0x17U))) 
                                      + (1U & (IData)(
                                                      (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                       >> 0x2fU)))));
                if ((1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                   >> 0x2fU)))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x18U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x17U)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x17U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x16U)));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1 
                    = (2U == (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN 
                    = (3U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0 
                    = (1U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                    = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                        | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? ((((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5) 
                                               >> 0x0fU)))) 
                             << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5)) 
                           * (((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5) 
                                                 >> 0x0fU)))) 
                               << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5)))
                        : ((1U & (((~ ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) 
                                       | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1))) 
                                   | (IData)((0U == 
                                              (0x7fffffffU 
                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                  | (IData)((0U == 
                                             (0x7fffffffU 
                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32)))))
                            ? 0U : (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                     << 0x0000001fU) 
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2) 
                                                - (IData)(0x7fU)))
                                          : 0U) << 0x00000017U) 
                                       | (0x007fffffU 
                                          & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                              & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                  ? 
                                                 ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))
                                                  : 
                                                 (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                   | (~ (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut))) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))))
                                              ? ((IData)(1U) 
                                                 + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm)
                                              : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T 
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__psumReg 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                    = (0x03ffffffU & ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                    = (0x07ffffffU & (((0x04000000U 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                                           << 1U)) 
                                       | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed) 
                                      + ((0x04000000U 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                                             << 1U)) 
                                         | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                    = (0x03ffffffU & ((0x04000000U 
                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       ? (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero 
                    = ((0x02000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                        ? 0U : ((0x01000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                 ? 1U : ((0x00800000U 
                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                          ? 2U : ((0x00400000U 
                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 3U
                                                   : 
                                                  ((0x00200000U 
                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                    ? 4U
                                                    : 
                                                   ((0x00100000U 
                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                     ? 5U
                                                     : 
                                                    ((0x00080000U 
                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                      ? 6U
                                                      : 
                                                     ((0x00040000U 
                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                       ? 7U
                                                       : 
                                                      ((0x00020000U 
                                                        & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                        ? 8U
                                                        : 
                                                       ((0x00010000U 
                                                         & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                         ? 9U
                                                         : 
                                                        ((0x00008000U 
                                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                          ? 0x0aU
                                                          : 
                                                         ((0x00004000U 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                           ? 0x0bU
                                                           : 
                                                          ((0x00002000U 
                                                            & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                            ? 0x0cU
                                                            : 
                                                           ((0x00001000U 
                                                             & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                             ? 0x0dU
                                                             : 
                                                            ((0x00000800U 
                                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                              ? 0x0eU
                                                              : 
                                                             ((0x00000400U 
                                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                               ? 0x0fU
                                                               : 
                                                              ((0x00000200U 
                                                                & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                ? 0x10U
                                                                : 
                                                               ((0x00000100U 
                                                                 & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                 ? 0x11U
                                                                 : 
                                                                ((0x00000080U 
                                                                  & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                  ? 0x12U
                                                                  : 
                                                                 ((0x00000040U 
                                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                   ? 0x13U
                                                                   : 
                                                                  ((0x00000020U 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                    ? 0x14U
                                                                    : 
                                                                   ((0x00000010U 
                                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                     ? 0x15U
                                                                     : 
                                                                    ((8U 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                      ? 0x16U
                                                                      : 
                                                                     ((4U 
                                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                       ? 0x17U
                                                                       : 
                                                                      (0x00000018U 
                                                                       | (1U 
                                                                          & (~ 
                                                                             (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                                                                              >> 1U))))))))))))))))))))))))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                    = (0x01ffffffffffffffULL & ((QData)((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)) 
                                                << (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3 
                    = (0x000000ffU & ((IData)(1U) + 
                                      ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                        ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__psumReg 
                                           >> 0x17U)
                                        : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                           >> 0x17U))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                            - (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1)))
                                                         : 0U) 
                                                       << 0x00000017U) 
                                                      | (0x007fffffU 
                                                         & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                                 ? 
                                                                ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                  >> 0x1aU) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))
                                                                 : 
                                                                (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                                  | (~ 
                                                                     (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                      >> 0x1aU))) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))))
                                                             ? 
                                                            ((IData)(1U) 
                                                             + (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U)))
                                                             : (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U))))))))));
            }
        }
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_5.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__io_validOut_REG));
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__psumReg = 0U;
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
        vlSelfRef.__PVT__wReg = 0U;
    } else {
        if (vlSymsp->TOP.svsimTestbench__DOT__io_clear) {
            vlSelfRef.__PVT__psumReg = 0U;
        } else if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__io_aOut_REG) 
                                             << 0x0000000dU)))));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.__PVT__wReg) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.__PVT__wReg))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.__PVT__wReg) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.__PVT__wReg) 
                                             << 0x0000000dU)))));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut 
                    = ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        ^ vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32) 
                       >> 0x1fU);
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                    = (0x0000ffffffffffffULL & ((0x0000000000800000ULL 
                                                 | (QData)((IData)(
                                                                   (0x007fffffU 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                                * (0x0000000000800000ULL 
                                                   | (QData)((IData)(
                                                                     (0x007fffffU 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2 
                    = (0x000001ffU & (((0x000000ffU 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                           >> 0x17U)) 
                                       + (0x000000ffU 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                             >> 0x17U))) 
                                      + (1U & (IData)(
                                                      (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                       >> 0x2fU)))));
                if ((1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                   >> 0x2fU)))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x18U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x17U)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x17U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x16U)));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1 
                    = (2U == (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN 
                    = (3U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0 
                    = (1U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                    = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                        | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? ((((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5) 
                                               >> 0x0fU)))) 
                             << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5)) 
                           * (((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5) 
                                                 >> 0x0fU)))) 
                               << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5)))
                        : ((1U & (((~ ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) 
                                       | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1))) 
                                   | (IData)((0U == 
                                              (0x7fffffffU 
                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                  | (IData)((0U == 
                                             (0x7fffffffU 
                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32)))))
                            ? 0U : (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                     << 0x0000001fU) 
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2) 
                                                - (IData)(0x7fU)))
                                          : 0U) << 0x00000017U) 
                                       | (0x007fffffU 
                                          & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                              & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                  ? 
                                                 ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))
                                                  : 
                                                 (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                   | (~ (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut))) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))))
                                              ? ((IData)(1U) 
                                                 + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm)
                                              : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T 
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__psumReg 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                    = (0x03ffffffU & ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                    = (0x07ffffffU & (((0x04000000U 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                                           << 1U)) 
                                       | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed) 
                                      + ((0x04000000U 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                                             << 1U)) 
                                         | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                    = (0x03ffffffU & ((0x04000000U 
                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       ? (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero 
                    = ((0x02000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                        ? 0U : ((0x01000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                 ? 1U : ((0x00800000U 
                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                          ? 2U : ((0x00400000U 
                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 3U
                                                   : 
                                                  ((0x00200000U 
                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                    ? 4U
                                                    : 
                                                   ((0x00100000U 
                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                     ? 5U
                                                     : 
                                                    ((0x00080000U 
                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                      ? 6U
                                                      : 
                                                     ((0x00040000U 
                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                       ? 7U
                                                       : 
                                                      ((0x00020000U 
                                                        & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                        ? 8U
                                                        : 
                                                       ((0x00010000U 
                                                         & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                         ? 9U
                                                         : 
                                                        ((0x00008000U 
                                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                          ? 0x0aU
                                                          : 
                                                         ((0x00004000U 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                           ? 0x0bU
                                                           : 
                                                          ((0x00002000U 
                                                            & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                            ? 0x0cU
                                                            : 
                                                           ((0x00001000U 
                                                             & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                             ? 0x0dU
                                                             : 
                                                            ((0x00000800U 
                                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                              ? 0x0eU
                                                              : 
                                                             ((0x00000400U 
                                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                               ? 0x0fU
                                                               : 
                                                              ((0x00000200U 
                                                                & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                ? 0x10U
                                                                : 
                                                               ((0x00000100U 
                                                                 & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                 ? 0x11U
                                                                 : 
                                                                ((0x00000080U 
                                                                  & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                  ? 0x12U
                                                                  : 
                                                                 ((0x00000040U 
                                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                   ? 0x13U
                                                                   : 
                                                                  ((0x00000020U 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                    ? 0x14U
                                                                    : 
                                                                   ((0x00000010U 
                                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                     ? 0x15U
                                                                     : 
                                                                    ((8U 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                      ? 0x16U
                                                                      : 
                                                                     ((4U 
                                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                       ? 0x17U
                                                                       : 
                                                                      (0x00000018U 
                                                                       | (1U 
                                                                          & (~ 
                                                                             (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                                                                              >> 1U))))))))))))))))))))))))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                    = (0x01ffffffffffffffULL & ((QData)((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)) 
                                                << (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3 
                    = (0x000000ffU & ((IData)(1U) + 
                                      ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                        ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__psumReg 
                                           >> 0x17U)
                                        : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                           >> 0x17U))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                            - (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1)))
                                                         : 0U) 
                                                       << 0x00000017U) 
                                                      | (0x007fffffU 
                                                         & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                                 ? 
                                                                ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                  >> 0x1aU) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))
                                                                 : 
                                                                (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                                  | (~ 
                                                                     (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                      >> 0x1aU))) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))))
                                                             ? 
                                                            ((IData)(1U) 
                                                             + (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U)))
                                                             : (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U))))))))));
            }
        }
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__io_rndOut_REG;
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_6.__PVT__psumReg);
        }
    }
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__io_validOut_REG));
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_7__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_7__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__io_validOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_7__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_7__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__psumReg = 0U;
        vlSelfRef.__PVT__wReg = 0U;
    } else {
        if (vlSymsp->TOP.svsimTestbench__DOT__io_clear) {
            vlSelfRef.__PVT__psumReg = 0U;
        } else if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_7.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__io_aOut_REG) 
                                             << 0x0000000dU)))));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.__PVT__wReg) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.__PVT__wReg))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.__PVT__wReg) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.__PVT__wReg) 
                                             << 0x0000000dU)))));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut 
                    = ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        ^ vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32) 
                       >> 0x1fU);
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                    = (0x0000ffffffffffffULL & ((0x0000000000800000ULL 
                                                 | (QData)((IData)(
                                                                   (0x007fffffU 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                                * (0x0000000000800000ULL 
                                                   | (QData)((IData)(
                                                                     (0x007fffffU 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2 
                    = (0x000001ffU & (((0x000000ffU 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                           >> 0x17U)) 
                                       + (0x000000ffU 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                             >> 0x17U))) 
                                      + (1U & (IData)(
                                                      (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                       >> 0x2fU)))));
                if ((1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                   >> 0x2fU)))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x18U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x17U)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x17U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x16U)));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1 
                    = (2U == (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN 
                    = (3U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0 
                    = (1U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                    = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                        | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? ((((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5) 
                                               >> 0x0fU)))) 
                             << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5)) 
                           * (((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5) 
                                                 >> 0x0fU)))) 
                               << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5)))
                        : ((1U & (((~ ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) 
                                       | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1))) 
                                   | (IData)((0U == 
                                              (0x7fffffffU 
                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                  | (IData)((0U == 
                                             (0x7fffffffU 
                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32)))))
                            ? 0U : (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                     << 0x0000001fU) 
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2) 
                                                - (IData)(0x7fU)))
                                          : 0U) << 0x00000017U) 
                                       | (0x007fffffU 
                                          & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                              & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                  ? 
                                                 ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))
                                                  : 
                                                 (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                   | (~ (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut))) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))))
                                              ? ((IData)(1U) 
                                                 + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm)
                                              : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T 
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_7.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_7.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_7.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_7.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_7.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_7.__PVT__psumReg 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                    = (0x03ffffffU & ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                    = (0x07ffffffU & (((0x04000000U 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                                           << 1U)) 
                                       | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed) 
                                      + ((0x04000000U 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                                             << 1U)) 
                                         | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                    = (0x03ffffffU & ((0x04000000U 
                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       ? (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero 
                    = ((0x02000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                        ? 0U : ((0x01000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                 ? 1U : ((0x00800000U 
                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                          ? 2U : ((0x00400000U 
                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 3U
                                                   : 
                                                  ((0x00200000U 
                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                    ? 4U
                                                    : 
                                                   ((0x00100000U 
                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                     ? 5U
                                                     : 
                                                    ((0x00080000U 
                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                      ? 6U
                                                      : 
                                                     ((0x00040000U 
                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                       ? 7U
                                                       : 
                                                      ((0x00020000U 
                                                        & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                        ? 8U
                                                        : 
                                                       ((0x00010000U 
                                                         & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                         ? 9U
                                                         : 
                                                        ((0x00008000U 
                                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                          ? 0x0aU
                                                          : 
                                                         ((0x00004000U 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                           ? 0x0bU
                                                           : 
                                                          ((0x00002000U 
                                                            & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                            ? 0x0cU
                                                            : 
                                                           ((0x00001000U 
                                                             & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                             ? 0x0dU
                                                             : 
                                                            ((0x00000800U 
                                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                              ? 0x0eU
                                                              : 
                                                             ((0x00000400U 
                                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                               ? 0x0fU
                                                               : 
                                                              ((0x00000200U 
                                                                & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                ? 0x10U
                                                                : 
                                                               ((0x00000100U 
                                                                 & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                 ? 0x11U
                                                                 : 
                                                                ((0x00000080U 
                                                                  & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                  ? 0x12U
                                                                  : 
                                                                 ((0x00000040U 
                                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                   ? 0x13U
                                                                   : 
                                                                  ((0x00000020U 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                    ? 0x14U
                                                                    : 
                                                                   ((0x00000010U 
                                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                     ? 0x15U
                                                                     : 
                                                                    ((8U 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                      ? 0x16U
                                                                      : 
                                                                     ((4U 
                                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                       ? 0x17U
                                                                       : 
                                                                      (0x00000018U 
                                                                       | (1U 
                                                                          & (~ 
                                                                             (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                                                                              >> 1U))))))))))))))))))))))))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                    = (0x01ffffffffffffffULL & ((QData)((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)) 
                                                << (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3 
                    = (0x000000ffU & ((IData)(1U) + 
                                      ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                        ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_7.__PVT__psumReg 
                                           >> 0x17U)
                                        : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                           >> 0x17U))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_7.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_7.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_7.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                            - (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1)))
                                                         : 0U) 
                                                       << 0x00000017U) 
                                                      | (0x007fffffU 
                                                         & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                                 ? 
                                                                ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                  >> 0x1aU) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))
                                                                 : 
                                                                (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                                  | (~ 
                                                                     (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                      >> 0x1aU))) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))))
                                                             ? 
                                                            ((IData)(1U) 
                                                             + (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U)))
                                                             : (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U))))))))));
            }
        }
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_2_7.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_7__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_7__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_0__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_0__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__psumReg = 0U;
        vlSelfRef.__PVT__wReg = 0U;
    } else {
        if (vlSymsp->TOP.svsimTestbench__DOT__io_clear) {
            vlSelfRef.__PVT__psumReg = 0U;
        } else if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_12) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_12) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_12)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP.svsimTestbench__DOT__io_aIn_12;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_12) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_12) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_12))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_12) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_12) 
                                             << 0x0000000dU)))));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.__PVT__wReg) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.__PVT__wReg))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.__PVT__wReg) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.__PVT__wReg) 
                                             << 0x0000000dU)))));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut 
                    = ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        ^ vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32) 
                       >> 0x1fU);
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                    = (0x0000ffffffffffffULL & ((0x0000000000800000ULL 
                                                 | (QData)((IData)(
                                                                   (0x007fffffU 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                                * (0x0000000000800000ULL 
                                                   | (QData)((IData)(
                                                                     (0x007fffffU 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2 
                    = (0x000001ffU & (((0x000000ffU 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                           >> 0x17U)) 
                                       + (0x000000ffU 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                             >> 0x17U))) 
                                      + (1U & (IData)(
                                                      (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                       >> 0x2fU)))));
                if ((1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                   >> 0x2fU)))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x18U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x17U)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x17U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x16U)));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1 
                    = (2U == (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN 
                    = (3U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0 
                    = (1U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                    = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                        | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? ((((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5) 
                                               >> 0x0fU)))) 
                             << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5)) 
                           * (((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5) 
                                                 >> 0x0fU)))) 
                               << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5)))
                        : ((1U & (((~ ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) 
                                       | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1))) 
                                   | (IData)((0U == 
                                              (0x7fffffffU 
                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                  | (IData)((0U == 
                                             (0x7fffffffU 
                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32)))))
                            ? 0U : (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                     << 0x0000001fU) 
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2) 
                                                - (IData)(0x7fU)))
                                          : 0U) << 0x00000017U) 
                                       | (0x007fffffU 
                                          & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                              & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                  ? 
                                                 ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))
                                                  : 
                                                 (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                   | (~ (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut))) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))))
                                              ? ((IData)(1U) 
                                                 + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm)
                                              : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T 
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__psumReg 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                    = (0x03ffffffU & ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                    = (0x07ffffffU & (((0x04000000U 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                                           << 1U)) 
                                       | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed) 
                                      + ((0x04000000U 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                                             << 1U)) 
                                         | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                    = (0x03ffffffU & ((0x04000000U 
                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       ? (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero 
                    = ((0x02000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                        ? 0U : ((0x01000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                 ? 1U : ((0x00800000U 
                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                          ? 2U : ((0x00400000U 
                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 3U
                                                   : 
                                                  ((0x00200000U 
                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                    ? 4U
                                                    : 
                                                   ((0x00100000U 
                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                     ? 5U
                                                     : 
                                                    ((0x00080000U 
                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                      ? 6U
                                                      : 
                                                     ((0x00040000U 
                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                       ? 7U
                                                       : 
                                                      ((0x00020000U 
                                                        & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                        ? 8U
                                                        : 
                                                       ((0x00010000U 
                                                         & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                         ? 9U
                                                         : 
                                                        ((0x00008000U 
                                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                          ? 0x0aU
                                                          : 
                                                         ((0x00004000U 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                           ? 0x0bU
                                                           : 
                                                          ((0x00002000U 
                                                            & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                            ? 0x0cU
                                                            : 
                                                           ((0x00001000U 
                                                             & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                             ? 0x0dU
                                                             : 
                                                            ((0x00000800U 
                                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                              ? 0x0eU
                                                              : 
                                                             ((0x00000400U 
                                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                               ? 0x0fU
                                                               : 
                                                              ((0x00000200U 
                                                                & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                ? 0x10U
                                                                : 
                                                               ((0x00000100U 
                                                                 & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                 ? 0x11U
                                                                 : 
                                                                ((0x00000080U 
                                                                  & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                  ? 0x12U
                                                                  : 
                                                                 ((0x00000040U 
                                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                   ? 0x13U
                                                                   : 
                                                                  ((0x00000020U 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                    ? 0x14U
                                                                    : 
                                                                   ((0x00000010U 
                                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                     ? 0x15U
                                                                     : 
                                                                    ((8U 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                      ? 0x16U
                                                                      : 
                                                                     ((4U 
                                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                       ? 0x17U
                                                                       : 
                                                                      (0x00000018U 
                                                                       | (1U 
                                                                          & (~ 
                                                                             (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                                                                              >> 1U))))))))))))))))))))))))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                    = (0x01ffffffffffffffULL & ((QData)((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)) 
                                                << (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3 
                    = (0x000000ffU & ((IData)(1U) + 
                                      ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                        ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__psumReg 
                                           >> 0x17U)
                                        : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                           >> 0x17U))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                            - (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1)))
                                                         : 0U) 
                                                       << 0x00000017U) 
                                                      | (0x007fffffU 
                                                         & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                                 ? 
                                                                ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                  >> 0x1aU) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))
                                                                 : 
                                                                (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                                  | (~ 
                                                                     (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                      >> 0x1aU))) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))))
                                                             ? 
                                                            ((IData)(1U) 
                                                             + (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U)))
                                                             : (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U))))))))));
            }
        }
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP.svsimTestbench__DOT__io_fmtIn;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP.svsimTestbench__DOT__io_rndIn;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_0__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_0__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_0.__PVT__io_validOut_REG));
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP.svsimTestbench__DOT__io_fmtIn;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP.svsimTestbench__DOT__io_rndIn;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_0__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_0__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_12));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_1__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_1__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__psumReg = 0U;
        vlSelfRef.__PVT__wReg = 0U;
    } else {
        if (vlSymsp->TOP.svsimTestbench__DOT__io_clear) {
            vlSelfRef.__PVT__psumReg = 0U;
        } else if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_0.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_0.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_0.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_0.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_0.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_0.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_0.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_0.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_0.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_0.__PVT__io_aOut_REG) 
                                             << 0x0000000dU)))));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.__PVT__wReg) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.__PVT__wReg))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.__PVT__wReg) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.__PVT__wReg) 
                                             << 0x0000000dU)))));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut 
                    = ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        ^ vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32) 
                       >> 0x1fU);
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                    = (0x0000ffffffffffffULL & ((0x0000000000800000ULL 
                                                 | (QData)((IData)(
                                                                   (0x007fffffU 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                                * (0x0000000000800000ULL 
                                                   | (QData)((IData)(
                                                                     (0x007fffffU 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2 
                    = (0x000001ffU & (((0x000000ffU 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                           >> 0x17U)) 
                                       + (0x000000ffU 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                             >> 0x17U))) 
                                      + (1U & (IData)(
                                                      (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                       >> 0x2fU)))));
                if ((1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                   >> 0x2fU)))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x18U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x17U)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x17U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x16U)));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1 
                    = (2U == (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN 
                    = (3U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0 
                    = (1U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                    = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                        | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? ((((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5) 
                                               >> 0x0fU)))) 
                             << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5)) 
                           * (((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5) 
                                                 >> 0x0fU)))) 
                               << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5)))
                        : ((1U & (((~ ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) 
                                       | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1))) 
                                   | (IData)((0U == 
                                              (0x7fffffffU 
                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                  | (IData)((0U == 
                                             (0x7fffffffU 
                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32)))))
                            ? 0U : (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                     << 0x0000001fU) 
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2) 
                                                - (IData)(0x7fU)))
                                          : 0U) << 0x00000017U) 
                                       | (0x007fffffU 
                                          & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                              & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                  ? 
                                                 ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))
                                                  : 
                                                 (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                   | (~ (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut))) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))))
                                              ? ((IData)(1U) 
                                                 + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm)
                                              : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T 
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__psumReg 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                    = (0x03ffffffU & ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                    = (0x07ffffffU & (((0x04000000U 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                                           << 1U)) 
                                       | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed) 
                                      + ((0x04000000U 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                                             << 1U)) 
                                         | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                    = (0x03ffffffU & ((0x04000000U 
                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       ? (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero 
                    = ((0x02000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                        ? 0U : ((0x01000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                 ? 1U : ((0x00800000U 
                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                          ? 2U : ((0x00400000U 
                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 3U
                                                   : 
                                                  ((0x00200000U 
                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                    ? 4U
                                                    : 
                                                   ((0x00100000U 
                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                     ? 5U
                                                     : 
                                                    ((0x00080000U 
                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                      ? 6U
                                                      : 
                                                     ((0x00040000U 
                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                       ? 7U
                                                       : 
                                                      ((0x00020000U 
                                                        & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                        ? 8U
                                                        : 
                                                       ((0x00010000U 
                                                         & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                         ? 9U
                                                         : 
                                                        ((0x00008000U 
                                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                          ? 0x0aU
                                                          : 
                                                         ((0x00004000U 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                           ? 0x0bU
                                                           : 
                                                          ((0x00002000U 
                                                            & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                            ? 0x0cU
                                                            : 
                                                           ((0x00001000U 
                                                             & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                             ? 0x0dU
                                                             : 
                                                            ((0x00000800U 
                                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                              ? 0x0eU
                                                              : 
                                                             ((0x00000400U 
                                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                               ? 0x0fU
                                                               : 
                                                              ((0x00000200U 
                                                                & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                ? 0x10U
                                                                : 
                                                               ((0x00000100U 
                                                                 & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                 ? 0x11U
                                                                 : 
                                                                ((0x00000080U 
                                                                  & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                  ? 0x12U
                                                                  : 
                                                                 ((0x00000040U 
                                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                   ? 0x13U
                                                                   : 
                                                                  ((0x00000020U 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                    ? 0x14U
                                                                    : 
                                                                   ((0x00000010U 
                                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                     ? 0x15U
                                                                     : 
                                                                    ((8U 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                      ? 0x16U
                                                                      : 
                                                                     ((4U 
                                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                       ? 0x17U
                                                                       : 
                                                                      (0x00000018U 
                                                                       | (1U 
                                                                          & (~ 
                                                                             (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                                                                              >> 1U))))))))))))))))))))))))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                    = (0x01ffffffffffffffULL & ((QData)((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)) 
                                                << (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3 
                    = (0x000000ffU & ((IData)(1U) + 
                                      ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                        ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__psumReg 
                                           >> 0x17U)
                                        : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                           >> 0x17U))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                            - (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1)))
                                                         : 0U) 
                                                       << 0x00000017U) 
                                                      | (0x007fffffU 
                                                         & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                                 ? 
                                                                ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                  >> 0x1aU) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))
                                                                 : 
                                                                (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                                  | (~ 
                                                                     (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                      >> 0x1aU))) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))))
                                                             ? 
                                                            ((IData)(1U) 
                                                             + (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U)))
                                                             : (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U))))))))));
            }
        }
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_1.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_0.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_0.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_1__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_1__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_0.__PVT__io_validOut_REG));
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_0.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_0.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_1__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_1__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_0.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_2__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_2__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__psumReg = 0U;
        vlSelfRef.__PVT__wReg = 0U;
    } else {
        if (vlSymsp->TOP.svsimTestbench__DOT__io_clear) {
            vlSelfRef.__PVT__psumReg = 0U;
        } else if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_1.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_1.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_1.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_1.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_1.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_1.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_1.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_1.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_1.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_1.__PVT__io_aOut_REG) 
                                             << 0x0000000dU)))));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.__PVT__wReg) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.__PVT__wReg))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.__PVT__wReg) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.__PVT__wReg) 
                                             << 0x0000000dU)))));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut 
                    = ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        ^ vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32) 
                       >> 0x1fU);
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                    = (0x0000ffffffffffffULL & ((0x0000000000800000ULL 
                                                 | (QData)((IData)(
                                                                   (0x007fffffU 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                                * (0x0000000000800000ULL 
                                                   | (QData)((IData)(
                                                                     (0x007fffffU 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2 
                    = (0x000001ffU & (((0x000000ffU 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                           >> 0x17U)) 
                                       + (0x000000ffU 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                             >> 0x17U))) 
                                      + (1U & (IData)(
                                                      (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                       >> 0x2fU)))));
                if ((1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                   >> 0x2fU)))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x18U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x17U)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x17U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x16U)));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1 
                    = (2U == (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN 
                    = (3U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0 
                    = (1U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                    = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                        | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? ((((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5) 
                                               >> 0x0fU)))) 
                             << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5)) 
                           * (((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5) 
                                                 >> 0x0fU)))) 
                               << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5)))
                        : ((1U & (((~ ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) 
                                       | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1))) 
                                   | (IData)((0U == 
                                              (0x7fffffffU 
                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                  | (IData)((0U == 
                                             (0x7fffffffU 
                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32)))))
                            ? 0U : (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                     << 0x0000001fU) 
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2) 
                                                - (IData)(0x7fU)))
                                          : 0U) << 0x00000017U) 
                                       | (0x007fffffU 
                                          & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                              & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                  ? 
                                                 ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))
                                                  : 
                                                 (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                   | (~ (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut))) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))))
                                              ? ((IData)(1U) 
                                                 + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm)
                                              : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T 
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__psumReg 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                    = (0x03ffffffU & ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                    = (0x07ffffffU & (((0x04000000U 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                                           << 1U)) 
                                       | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed) 
                                      + ((0x04000000U 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                                             << 1U)) 
                                         | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                    = (0x03ffffffU & ((0x04000000U 
                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       ? (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero 
                    = ((0x02000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                        ? 0U : ((0x01000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                 ? 1U : ((0x00800000U 
                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                          ? 2U : ((0x00400000U 
                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 3U
                                                   : 
                                                  ((0x00200000U 
                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                    ? 4U
                                                    : 
                                                   ((0x00100000U 
                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                     ? 5U
                                                     : 
                                                    ((0x00080000U 
                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                      ? 6U
                                                      : 
                                                     ((0x00040000U 
                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                       ? 7U
                                                       : 
                                                      ((0x00020000U 
                                                        & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                        ? 8U
                                                        : 
                                                       ((0x00010000U 
                                                         & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                         ? 9U
                                                         : 
                                                        ((0x00008000U 
                                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                          ? 0x0aU
                                                          : 
                                                         ((0x00004000U 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                           ? 0x0bU
                                                           : 
                                                          ((0x00002000U 
                                                            & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                            ? 0x0cU
                                                            : 
                                                           ((0x00001000U 
                                                             & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                             ? 0x0dU
                                                             : 
                                                            ((0x00000800U 
                                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                              ? 0x0eU
                                                              : 
                                                             ((0x00000400U 
                                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                               ? 0x0fU
                                                               : 
                                                              ((0x00000200U 
                                                                & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                ? 0x10U
                                                                : 
                                                               ((0x00000100U 
                                                                 & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                 ? 0x11U
                                                                 : 
                                                                ((0x00000080U 
                                                                  & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                  ? 0x12U
                                                                  : 
                                                                 ((0x00000040U 
                                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                   ? 0x13U
                                                                   : 
                                                                  ((0x00000020U 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                    ? 0x14U
                                                                    : 
                                                                   ((0x00000010U 
                                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                     ? 0x15U
                                                                     : 
                                                                    ((8U 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                      ? 0x16U
                                                                      : 
                                                                     ((4U 
                                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                       ? 0x17U
                                                                       : 
                                                                      (0x00000018U 
                                                                       | (1U 
                                                                          & (~ 
                                                                             (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                                                                              >> 1U))))))))))))))))))))))))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                    = (0x01ffffffffffffffULL & ((QData)((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)) 
                                                << (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3 
                    = (0x000000ffU & ((IData)(1U) + 
                                      ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                        ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__psumReg 
                                           >> 0x17U)
                                        : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                           >> 0x17U))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                            - (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1)))
                                                         : 0U) 
                                                       << 0x00000017U) 
                                                      | (0x007fffffU 
                                                         & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                                 ? 
                                                                ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                  >> 0x1aU) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))
                                                                 : 
                                                                (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                                  | (~ 
                                                                     (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                      >> 0x1aU))) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))))
                                                             ? 
                                                            ((IData)(1U) 
                                                             + (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U)))
                                                             : (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U))))))))));
            }
        }
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_2.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_1.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_1.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_2__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_2__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_1.__PVT__io_validOut_REG));
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_1.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_1.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_2__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_2__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_1.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_3__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_3__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__psumReg = 0U;
        vlSelfRef.__PVT__wReg = 0U;
    } else {
        if (vlSymsp->TOP.svsimTestbench__DOT__io_clear) {
            vlSelfRef.__PVT__psumReg = 0U;
        } else if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_2.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_2.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_2.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_2.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_2.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_2.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_2.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_2.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_2.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_2.__PVT__io_aOut_REG) 
                                             << 0x0000000dU)))));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.__PVT__wReg) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.__PVT__wReg))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.__PVT__wReg) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.__PVT__wReg) 
                                             << 0x0000000dU)))));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut 
                    = ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        ^ vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32) 
                       >> 0x1fU);
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                    = (0x0000ffffffffffffULL & ((0x0000000000800000ULL 
                                                 | (QData)((IData)(
                                                                   (0x007fffffU 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                                * (0x0000000000800000ULL 
                                                   | (QData)((IData)(
                                                                     (0x007fffffU 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2 
                    = (0x000001ffU & (((0x000000ffU 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                           >> 0x17U)) 
                                       + (0x000000ffU 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                             >> 0x17U))) 
                                      + (1U & (IData)(
                                                      (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                       >> 0x2fU)))));
                if ((1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                   >> 0x2fU)))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x18U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x17U)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x17U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x16U)));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1 
                    = (2U == (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN 
                    = (3U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0 
                    = (1U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                    = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                        | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? ((((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5) 
                                               >> 0x0fU)))) 
                             << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5)) 
                           * (((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5) 
                                                 >> 0x0fU)))) 
                               << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5)))
                        : ((1U & (((~ ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) 
                                       | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1))) 
                                   | (IData)((0U == 
                                              (0x7fffffffU 
                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                  | (IData)((0U == 
                                             (0x7fffffffU 
                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32)))))
                            ? 0U : (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                     << 0x0000001fU) 
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2) 
                                                - (IData)(0x7fU)))
                                          : 0U) << 0x00000017U) 
                                       | (0x007fffffU 
                                          & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                              & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                  ? 
                                                 ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))
                                                  : 
                                                 (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                   | (~ (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut))) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))))
                                              ? ((IData)(1U) 
                                                 + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm)
                                              : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T 
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__psumReg 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                    = (0x03ffffffU & ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                    = (0x07ffffffU & (((0x04000000U 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                                           << 1U)) 
                                       | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed) 
                                      + ((0x04000000U 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                                             << 1U)) 
                                         | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                    = (0x03ffffffU & ((0x04000000U 
                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       ? (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero 
                    = ((0x02000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                        ? 0U : ((0x01000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                 ? 1U : ((0x00800000U 
                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                          ? 2U : ((0x00400000U 
                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 3U
                                                   : 
                                                  ((0x00200000U 
                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                    ? 4U
                                                    : 
                                                   ((0x00100000U 
                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                     ? 5U
                                                     : 
                                                    ((0x00080000U 
                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                      ? 6U
                                                      : 
                                                     ((0x00040000U 
                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                       ? 7U
                                                       : 
                                                      ((0x00020000U 
                                                        & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                        ? 8U
                                                        : 
                                                       ((0x00010000U 
                                                         & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                         ? 9U
                                                         : 
                                                        ((0x00008000U 
                                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                          ? 0x0aU
                                                          : 
                                                         ((0x00004000U 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                           ? 0x0bU
                                                           : 
                                                          ((0x00002000U 
                                                            & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                            ? 0x0cU
                                                            : 
                                                           ((0x00001000U 
                                                             & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                             ? 0x0dU
                                                             : 
                                                            ((0x00000800U 
                                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                              ? 0x0eU
                                                              : 
                                                             ((0x00000400U 
                                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                               ? 0x0fU
                                                               : 
                                                              ((0x00000200U 
                                                                & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                ? 0x10U
                                                                : 
                                                               ((0x00000100U 
                                                                 & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                 ? 0x11U
                                                                 : 
                                                                ((0x00000080U 
                                                                  & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                  ? 0x12U
                                                                  : 
                                                                 ((0x00000040U 
                                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                   ? 0x13U
                                                                   : 
                                                                  ((0x00000020U 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                    ? 0x14U
                                                                    : 
                                                                   ((0x00000010U 
                                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                     ? 0x15U
                                                                     : 
                                                                    ((8U 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                      ? 0x16U
                                                                      : 
                                                                     ((4U 
                                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                       ? 0x17U
                                                                       : 
                                                                      (0x00000018U 
                                                                       | (1U 
                                                                          & (~ 
                                                                             (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                                                                              >> 1U))))))))))))))))))))))))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                    = (0x01ffffffffffffffULL & ((QData)((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)) 
                                                << (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3 
                    = (0x000000ffU & ((IData)(1U) + 
                                      ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                        ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__psumReg 
                                           >> 0x17U)
                                        : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                           >> 0x17U))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                            - (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1)))
                                                         : 0U) 
                                                       << 0x00000017U) 
                                                      | (0x007fffffU 
                                                         & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                                 ? 
                                                                ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                  >> 0x1aU) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))
                                                                 : 
                                                                (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                                  | (~ 
                                                                     (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                      >> 0x1aU))) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))))
                                                             ? 
                                                            ((IData)(1U) 
                                                             + (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U)))
                                                             : (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U))))))))));
            }
        }
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_3.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_2.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_2.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_3__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_3__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_2.__PVT__io_validOut_REG));
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_2.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_2.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_3__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_3__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_2.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_4__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_4__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__psumReg = 0U;
        vlSelfRef.__PVT__wReg = 0U;
    } else {
        if (vlSymsp->TOP.svsimTestbench__DOT__io_clear) {
            vlSelfRef.__PVT__psumReg = 0U;
        } else if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_3.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_3.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_3.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_3.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_3.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_3.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_3.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_3.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_3.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_3.__PVT__io_aOut_REG) 
                                             << 0x0000000dU)))));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.__PVT__wReg) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.__PVT__wReg))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.__PVT__wReg) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.__PVT__wReg) 
                                             << 0x0000000dU)))));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut 
                    = ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        ^ vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32) 
                       >> 0x1fU);
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                    = (0x0000ffffffffffffULL & ((0x0000000000800000ULL 
                                                 | (QData)((IData)(
                                                                   (0x007fffffU 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                                * (0x0000000000800000ULL 
                                                   | (QData)((IData)(
                                                                     (0x007fffffU 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2 
                    = (0x000001ffU & (((0x000000ffU 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                           >> 0x17U)) 
                                       + (0x000000ffU 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                             >> 0x17U))) 
                                      + (1U & (IData)(
                                                      (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                       >> 0x2fU)))));
                if ((1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                   >> 0x2fU)))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x18U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x17U)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x17U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x16U)));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1 
                    = (2U == (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN 
                    = (3U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0 
                    = (1U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                    = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                        | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? ((((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5) 
                                               >> 0x0fU)))) 
                             << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5)) 
                           * (((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5) 
                                                 >> 0x0fU)))) 
                               << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5)))
                        : ((1U & (((~ ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) 
                                       | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1))) 
                                   | (IData)((0U == 
                                              (0x7fffffffU 
                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                  | (IData)((0U == 
                                             (0x7fffffffU 
                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32)))))
                            ? 0U : (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                     << 0x0000001fU) 
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2) 
                                                - (IData)(0x7fU)))
                                          : 0U) << 0x00000017U) 
                                       | (0x007fffffU 
                                          & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                              & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                  ? 
                                                 ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))
                                                  : 
                                                 (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                   | (~ (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut))) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))))
                                              ? ((IData)(1U) 
                                                 + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm)
                                              : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T 
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__psumReg 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                    = (0x03ffffffU & ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                    = (0x07ffffffU & (((0x04000000U 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                                           << 1U)) 
                                       | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed) 
                                      + ((0x04000000U 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                                             << 1U)) 
                                         | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                    = (0x03ffffffU & ((0x04000000U 
                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       ? (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero 
                    = ((0x02000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                        ? 0U : ((0x01000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                 ? 1U : ((0x00800000U 
                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                          ? 2U : ((0x00400000U 
                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 3U
                                                   : 
                                                  ((0x00200000U 
                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                    ? 4U
                                                    : 
                                                   ((0x00100000U 
                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                     ? 5U
                                                     : 
                                                    ((0x00080000U 
                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                      ? 6U
                                                      : 
                                                     ((0x00040000U 
                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                       ? 7U
                                                       : 
                                                      ((0x00020000U 
                                                        & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                        ? 8U
                                                        : 
                                                       ((0x00010000U 
                                                         & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                         ? 9U
                                                         : 
                                                        ((0x00008000U 
                                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                          ? 0x0aU
                                                          : 
                                                         ((0x00004000U 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                           ? 0x0bU
                                                           : 
                                                          ((0x00002000U 
                                                            & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                            ? 0x0cU
                                                            : 
                                                           ((0x00001000U 
                                                             & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                             ? 0x0dU
                                                             : 
                                                            ((0x00000800U 
                                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                              ? 0x0eU
                                                              : 
                                                             ((0x00000400U 
                                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                               ? 0x0fU
                                                               : 
                                                              ((0x00000200U 
                                                                & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                ? 0x10U
                                                                : 
                                                               ((0x00000100U 
                                                                 & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                 ? 0x11U
                                                                 : 
                                                                ((0x00000080U 
                                                                  & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                  ? 0x12U
                                                                  : 
                                                                 ((0x00000040U 
                                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                   ? 0x13U
                                                                   : 
                                                                  ((0x00000020U 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                    ? 0x14U
                                                                    : 
                                                                   ((0x00000010U 
                                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                     ? 0x15U
                                                                     : 
                                                                    ((8U 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                      ? 0x16U
                                                                      : 
                                                                     ((4U 
                                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                       ? 0x17U
                                                                       : 
                                                                      (0x00000018U 
                                                                       | (1U 
                                                                          & (~ 
                                                                             (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                                                                              >> 1U))))))))))))))))))))))))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                    = (0x01ffffffffffffffULL & ((QData)((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)) 
                                                << (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3 
                    = (0x000000ffU & ((IData)(1U) + 
                                      ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                        ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__psumReg 
                                           >> 0x17U)
                                        : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                           >> 0x17U))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                            - (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1)))
                                                         : 0U) 
                                                       << 0x00000017U) 
                                                      | (0x007fffffU 
                                                         & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                                 ? 
                                                                ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                  >> 0x1aU) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))
                                                                 : 
                                                                (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                                  | (~ 
                                                                     (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                      >> 0x1aU))) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))))
                                                             ? 
                                                            ((IData)(1U) 
                                                             + (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U)))
                                                             : (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U))))))))));
            }
        }
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_4.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_3.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_3.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_4__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_4__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_3.__PVT__io_validOut_REG));
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_3.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_3.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_4__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_4__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_3.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_5__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_5__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__psumReg = 0U;
        vlSelfRef.__PVT__wReg = 0U;
    } else {
        if (vlSymsp->TOP.svsimTestbench__DOT__io_clear) {
            vlSelfRef.__PVT__psumReg = 0U;
        } else if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_4.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_4.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_4.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_4.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_4.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_4.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_4.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_4.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_4.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_4.__PVT__io_aOut_REG) 
                                             << 0x0000000dU)))));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.__PVT__wReg) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.__PVT__wReg))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.__PVT__wReg) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.__PVT__wReg) 
                                             << 0x0000000dU)))));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut 
                    = ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        ^ vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32) 
                       >> 0x1fU);
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                    = (0x0000ffffffffffffULL & ((0x0000000000800000ULL 
                                                 | (QData)((IData)(
                                                                   (0x007fffffU 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                                * (0x0000000000800000ULL 
                                                   | (QData)((IData)(
                                                                     (0x007fffffU 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2 
                    = (0x000001ffU & (((0x000000ffU 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                           >> 0x17U)) 
                                       + (0x000000ffU 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                             >> 0x17U))) 
                                      + (1U & (IData)(
                                                      (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                       >> 0x2fU)))));
                if ((1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                   >> 0x2fU)))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x18U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x17U)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x17U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x16U)));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1 
                    = (2U == (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN 
                    = (3U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0 
                    = (1U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                    = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                        | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? ((((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5) 
                                               >> 0x0fU)))) 
                             << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5)) 
                           * (((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5) 
                                                 >> 0x0fU)))) 
                               << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5)))
                        : ((1U & (((~ ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) 
                                       | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1))) 
                                   | (IData)((0U == 
                                              (0x7fffffffU 
                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                  | (IData)((0U == 
                                             (0x7fffffffU 
                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32)))))
                            ? 0U : (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                     << 0x0000001fU) 
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2) 
                                                - (IData)(0x7fU)))
                                          : 0U) << 0x00000017U) 
                                       | (0x007fffffU 
                                          & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                              & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                  ? 
                                                 ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))
                                                  : 
                                                 (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                   | (~ (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut))) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))))
                                              ? ((IData)(1U) 
                                                 + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm)
                                              : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T 
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__psumReg 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                    = (0x03ffffffU & ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                    = (0x07ffffffU & (((0x04000000U 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                                           << 1U)) 
                                       | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed) 
                                      + ((0x04000000U 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                                             << 1U)) 
                                         | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                    = (0x03ffffffU & ((0x04000000U 
                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       ? (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero 
                    = ((0x02000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                        ? 0U : ((0x01000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                 ? 1U : ((0x00800000U 
                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                          ? 2U : ((0x00400000U 
                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 3U
                                                   : 
                                                  ((0x00200000U 
                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                    ? 4U
                                                    : 
                                                   ((0x00100000U 
                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                     ? 5U
                                                     : 
                                                    ((0x00080000U 
                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                      ? 6U
                                                      : 
                                                     ((0x00040000U 
                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                       ? 7U
                                                       : 
                                                      ((0x00020000U 
                                                        & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                        ? 8U
                                                        : 
                                                       ((0x00010000U 
                                                         & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                         ? 9U
                                                         : 
                                                        ((0x00008000U 
                                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                          ? 0x0aU
                                                          : 
                                                         ((0x00004000U 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                           ? 0x0bU
                                                           : 
                                                          ((0x00002000U 
                                                            & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                            ? 0x0cU
                                                            : 
                                                           ((0x00001000U 
                                                             & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                             ? 0x0dU
                                                             : 
                                                            ((0x00000800U 
                                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                              ? 0x0eU
                                                              : 
                                                             ((0x00000400U 
                                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                               ? 0x0fU
                                                               : 
                                                              ((0x00000200U 
                                                                & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                ? 0x10U
                                                                : 
                                                               ((0x00000100U 
                                                                 & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                 ? 0x11U
                                                                 : 
                                                                ((0x00000080U 
                                                                  & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                  ? 0x12U
                                                                  : 
                                                                 ((0x00000040U 
                                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                   ? 0x13U
                                                                   : 
                                                                  ((0x00000020U 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                    ? 0x14U
                                                                    : 
                                                                   ((0x00000010U 
                                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                     ? 0x15U
                                                                     : 
                                                                    ((8U 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                      ? 0x16U
                                                                      : 
                                                                     ((4U 
                                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                       ? 0x17U
                                                                       : 
                                                                      (0x00000018U 
                                                                       | (1U 
                                                                          & (~ 
                                                                             (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                                                                              >> 1U))))))))))))))))))))))))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                    = (0x01ffffffffffffffULL & ((QData)((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)) 
                                                << (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3 
                    = (0x000000ffU & ((IData)(1U) + 
                                      ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                        ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__psumReg 
                                           >> 0x17U)
                                        : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                           >> 0x17U))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                            - (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1)))
                                                         : 0U) 
                                                       << 0x00000017U) 
                                                      | (0x007fffffU 
                                                         & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                                 ? 
                                                                ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                  >> 0x1aU) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))
                                                                 : 
                                                                (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                                  | (~ 
                                                                     (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                      >> 0x1aU))) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))))
                                                             ? 
                                                            ((IData)(1U) 
                                                             + (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U)))
                                                             : (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U))))))))));
            }
        }
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_5.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_4.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_4.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_5__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_5__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_4.__PVT__io_validOut_REG));
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_4.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_4.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_5__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_5__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_4.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_6__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_6__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__psumReg = 0U;
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
        vlSelfRef.__PVT__wReg = 0U;
    } else {
        if (vlSymsp->TOP.svsimTestbench__DOT__io_clear) {
            vlSelfRef.__PVT__psumReg = 0U;
        } else if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_5.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_5.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_5.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_5.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_5.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_5.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_5.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_5.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_5.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_5.__PVT__io_aOut_REG) 
                                             << 0x0000000dU)))));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.__PVT__wReg) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.__PVT__wReg))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.__PVT__wReg) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.__PVT__wReg) 
                                             << 0x0000000dU)))));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut 
                    = ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        ^ vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32) 
                       >> 0x1fU);
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                    = (0x0000ffffffffffffULL & ((0x0000000000800000ULL 
                                                 | (QData)((IData)(
                                                                   (0x007fffffU 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                                * (0x0000000000800000ULL 
                                                   | (QData)((IData)(
                                                                     (0x007fffffU 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2 
                    = (0x000001ffU & (((0x000000ffU 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                           >> 0x17U)) 
                                       + (0x000000ffU 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                             >> 0x17U))) 
                                      + (1U & (IData)(
                                                      (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                       >> 0x2fU)))));
                if ((1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                   >> 0x2fU)))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x18U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x17U)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x17U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x16U)));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1 
                    = (2U == (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN 
                    = (3U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0 
                    = (1U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                    = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                        | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? ((((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5) 
                                               >> 0x0fU)))) 
                             << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5)) 
                           * (((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5) 
                                                 >> 0x0fU)))) 
                               << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5)))
                        : ((1U & (((~ ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) 
                                       | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1))) 
                                   | (IData)((0U == 
                                              (0x7fffffffU 
                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                  | (IData)((0U == 
                                             (0x7fffffffU 
                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32)))))
                            ? 0U : (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                     << 0x0000001fU) 
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2) 
                                                - (IData)(0x7fU)))
                                          : 0U) << 0x00000017U) 
                                       | (0x007fffffU 
                                          & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                              & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                  ? 
                                                 ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))
                                                  : 
                                                 (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                   | (~ (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut))) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))))
                                              ? ((IData)(1U) 
                                                 + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm)
                                              : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T 
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__psumReg 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                    = (0x03ffffffU & ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                    = (0x07ffffffU & (((0x04000000U 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                                           << 1U)) 
                                       | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed) 
                                      + ((0x04000000U 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                                             << 1U)) 
                                         | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                    = (0x03ffffffU & ((0x04000000U 
                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       ? (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero 
                    = ((0x02000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                        ? 0U : ((0x01000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                 ? 1U : ((0x00800000U 
                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                          ? 2U : ((0x00400000U 
                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 3U
                                                   : 
                                                  ((0x00200000U 
                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                    ? 4U
                                                    : 
                                                   ((0x00100000U 
                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                     ? 5U
                                                     : 
                                                    ((0x00080000U 
                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                      ? 6U
                                                      : 
                                                     ((0x00040000U 
                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                       ? 7U
                                                       : 
                                                      ((0x00020000U 
                                                        & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                        ? 8U
                                                        : 
                                                       ((0x00010000U 
                                                         & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                         ? 9U
                                                         : 
                                                        ((0x00008000U 
                                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                          ? 0x0aU
                                                          : 
                                                         ((0x00004000U 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                           ? 0x0bU
                                                           : 
                                                          ((0x00002000U 
                                                            & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                            ? 0x0cU
                                                            : 
                                                           ((0x00001000U 
                                                             & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                             ? 0x0dU
                                                             : 
                                                            ((0x00000800U 
                                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                              ? 0x0eU
                                                              : 
                                                             ((0x00000400U 
                                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                               ? 0x0fU
                                                               : 
                                                              ((0x00000200U 
                                                                & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                ? 0x10U
                                                                : 
                                                               ((0x00000100U 
                                                                 & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                 ? 0x11U
                                                                 : 
                                                                ((0x00000080U 
                                                                  & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                  ? 0x12U
                                                                  : 
                                                                 ((0x00000040U 
                                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                   ? 0x13U
                                                                   : 
                                                                  ((0x00000020U 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                    ? 0x14U
                                                                    : 
                                                                   ((0x00000010U 
                                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                     ? 0x15U
                                                                     : 
                                                                    ((8U 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                      ? 0x16U
                                                                      : 
                                                                     ((4U 
                                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                       ? 0x17U
                                                                       : 
                                                                      (0x00000018U 
                                                                       | (1U 
                                                                          & (~ 
                                                                             (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                                                                              >> 1U))))))))))))))))))))))))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                    = (0x01ffffffffffffffULL & ((QData)((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)) 
                                                << (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3 
                    = (0x000000ffU & ((IData)(1U) + 
                                      ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                        ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__psumReg 
                                           >> 0x17U)
                                        : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                           >> 0x17U))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                            - (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1)))
                                                         : 0U) 
                                                       << 0x00000017U) 
                                                      | (0x007fffffU 
                                                         & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                                 ? 
                                                                ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                  >> 0x1aU) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))
                                                                 : 
                                                                (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                                  | (~ 
                                                                     (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                      >> 0x1aU))) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))))
                                                             ? 
                                                            ((IData)(1U) 
                                                             + (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U)))
                                                             : (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U))))))))));
            }
        }
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_5.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_5.__PVT__io_rndOut_REG;
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_6.__PVT__psumReg);
        }
    }
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_5.__PVT__io_validOut_REG));
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_5.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_5.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_6__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_6__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_5.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_7__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_7__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_6.__PVT__io_validOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_7__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_7__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__psumReg = 0U;
        vlSelfRef.__PVT__wReg = 0U;
    } else {
        if (vlSymsp->TOP.svsimTestbench__DOT__io_clear) {
            vlSelfRef.__PVT__psumReg = 0U;
        } else if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_7.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_6.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_6.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_6.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_6.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_6.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_6.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_6.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_6.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_6.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_6.__PVT__io_aOut_REG) 
                                             << 0x0000000dU)))));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.__PVT__wReg) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.__PVT__wReg))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.__PVT__wReg) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.__PVT__wReg) 
                                             << 0x0000000dU)))));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut 
                    = ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        ^ vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32) 
                       >> 0x1fU);
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                    = (0x0000ffffffffffffULL & ((0x0000000000800000ULL 
                                                 | (QData)((IData)(
                                                                   (0x007fffffU 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                                * (0x0000000000800000ULL 
                                                   | (QData)((IData)(
                                                                     (0x007fffffU 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2 
                    = (0x000001ffU & (((0x000000ffU 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                           >> 0x17U)) 
                                       + (0x000000ffU 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                             >> 0x17U))) 
                                      + (1U & (IData)(
                                                      (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                       >> 0x2fU)))));
                if ((1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                   >> 0x2fU)))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x18U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x17U)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x17U)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x16U)));
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1 
                    = (2U == (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN 
                    = (3U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0 
                    = (1U != (IData)(vlSelfRef.__PVT__rndReg));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                    = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                        | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? ((((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5) 
                                               >> 0x0fU)))) 
                             << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5)) 
                           * (((- (IData)((1U & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5) 
                                                 >> 0x0fU)))) 
                               << 0x00000010U) | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5)))
                        : ((1U & (((~ ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) 
                                       | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1))) 
                                   | (IData)((0U == 
                                              (0x7fffffffU 
                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                  | (IData)((0U == 
                                             (0x7fffffffU 
                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32)))))
                            ? 0U : (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                     << 0x0000001fU) 
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2) 
                                                - (IData)(0x7fU)))
                                          : 0U) << 0x00000017U) 
                                       | (0x007fffffU 
                                          & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                              & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                  ? 
                                                 ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))
                                                  : 
                                                 (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                   | (~ (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut))) 
                                                  & (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))))
                                              ? ((IData)(1U) 
                                                 + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm)
                                              : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T 
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_7.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_7.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_7.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_7.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_7.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_7.__PVT__psumReg 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                    = (0x03ffffffU & ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                    = (0x07ffffffU & (((0x04000000U 
                                        & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                                           << 1U)) 
                                       | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed) 
                                      + ((0x04000000U 
                                          & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                                             << 1U)) 
                                         | vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                    = (0x03ffffffU & ((0x04000000U 
                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       ? (- vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       : vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero 
                    = ((0x02000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                        ? 0U : ((0x01000000U & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                 ? 1U : ((0x00800000U 
                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                          ? 2U : ((0x00400000U 
                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 3U
                                                   : 
                                                  ((0x00200000U 
                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                    ? 4U
                                                    : 
                                                   ((0x00100000U 
                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                     ? 5U
                                                     : 
                                                    ((0x00080000U 
                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                      ? 6U
                                                      : 
                                                     ((0x00040000U 
                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                       ? 7U
                                                       : 
                                                      ((0x00020000U 
                                                        & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                        ? 8U
                                                        : 
                                                       ((0x00010000U 
                                                         & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                         ? 9U
                                                         : 
                                                        ((0x00008000U 
                                                          & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                          ? 0x0aU
                                                          : 
                                                         ((0x00004000U 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                           ? 0x0bU
                                                           : 
                                                          ((0x00002000U 
                                                            & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                            ? 0x0cU
                                                            : 
                                                           ((0x00001000U 
                                                             & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                             ? 0x0dU
                                                             : 
                                                            ((0x00000800U 
                                                              & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                              ? 0x0eU
                                                              : 
                                                             ((0x00000400U 
                                                               & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                               ? 0x0fU
                                                               : 
                                                              ((0x00000200U 
                                                                & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                ? 0x10U
                                                                : 
                                                               ((0x00000100U 
                                                                 & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                 ? 0x11U
                                                                 : 
                                                                ((0x00000080U 
                                                                  & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                  ? 0x12U
                                                                  : 
                                                                 ((0x00000040U 
                                                                   & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                   ? 0x13U
                                                                   : 
                                                                  ((0x00000020U 
                                                                    & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                    ? 0x14U
                                                                    : 
                                                                   ((0x00000010U 
                                                                     & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                     ? 0x15U
                                                                     : 
                                                                    ((8U 
                                                                      & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                      ? 0x16U
                                                                      : 
                                                                     ((4U 
                                                                       & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                       ? 0x17U
                                                                       : 
                                                                      (0x00000018U 
                                                                       | (1U 
                                                                          & (~ 
                                                                             (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                                                                              >> 1U))))))))))))))))))))))))))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                    = (0x01ffffffffffffffULL & ((QData)((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)) 
                                                << (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3 
                    = (0x000000ffU & ((IData)(1U) + 
                                      ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                        ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_7.__PVT__psumReg 
                                           >> 0x17U)
                                        : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                           >> 0x17U))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_7.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_7.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_7.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                            - (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1)))
                                                         : 0U) 
                                                       << 0x00000017U) 
                                                      | (0x007fffffU 
                                                         & (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                                 ? 
                                                                ((vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                  >> 0x1aU) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))
                                                                 : 
                                                                (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                                  | (~ 
                                                                     (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                                      >> 0x1aU))) 
                                                                 & (IData)(
                                                                           (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                            >> 1U)))))
                                                             ? 
                                                            ((IData)(1U) 
                                                             + (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U)))
                                                             : (IData)(
                                                                       (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                        >> 2U))))))))));
            }
        }
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_3_7.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_6.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_1_0__pes_4_6.__PVT__io_rndOut_REG;
    }
}
