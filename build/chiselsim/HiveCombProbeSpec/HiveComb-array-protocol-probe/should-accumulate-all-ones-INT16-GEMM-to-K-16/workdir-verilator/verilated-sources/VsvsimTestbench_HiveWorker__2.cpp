// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See VsvsimTestbench.h for the primary calling header

#include "VsvsimTestbench__pch.h"

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_7__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_7__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_6.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_7__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_7__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_6.__PVT__io_validOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0__0\n"); );
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
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_0.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_7.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_6) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_6) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_6)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP.svsimTestbench__DOT__io_aIn_6;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_6) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_6) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_6))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_6) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_6) 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum 
                    = (0x000001ffU & ((0x000000ffU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                          >> 0x17U)) 
                                      + (0x000000ffU 
                                         & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                            >> 0x17U))));
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
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum) 
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
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_0.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax 
                    = (0x000000ffU & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                       ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_0.__PVT__psumReg 
                                          >> 0x17U)
                                       : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_0.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_0.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_0.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_0.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_0.__PVT__psumReg 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_0.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_0.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_0.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
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
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_0.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP.svsimTestbench__DOT__io_fmtIn;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP.svsimTestbench__DOT__io_rndIn;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_7.__PVT__io_validOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0__3(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0__3\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_6));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1__0\n"); );
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
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_1.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__io_aOut_REG) 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum 
                    = (0x000001ffU & ((0x000000ffU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                          >> 0x17U)) 
                                      + (0x000000ffU 
                                         & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                            >> 0x17U))));
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
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum) 
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
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_1.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax 
                    = (0x000000ffU & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                       ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_1.__PVT__psumReg 
                                          >> 0x17U)
                                       : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_1.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_1.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_1.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_1.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_1.__PVT__psumReg 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_1.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_1.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_1.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
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
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_1.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__io_validOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1__3(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1__3\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2__0\n"); );
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
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_2.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__io_aOut_REG) 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum 
                    = (0x000001ffU & ((0x000000ffU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                          >> 0x17U)) 
                                      + (0x000000ffU 
                                         & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                            >> 0x17U))));
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
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum) 
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
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_2.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax 
                    = (0x000000ffU & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                       ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_2.__PVT__psumReg 
                                          >> 0x17U)
                                       : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_2.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_2.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_2.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_2.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_2.__PVT__psumReg 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_2.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_2.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_2.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
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
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_2.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__io_validOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2__3(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2__3\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3__0\n"); );
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
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_3.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__io_aOut_REG) 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum 
                    = (0x000001ffU & ((0x000000ffU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                          >> 0x17U)) 
                                      + (0x000000ffU 
                                         & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                            >> 0x17U))));
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
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum) 
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
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_3.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax 
                    = (0x000000ffU & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                       ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_3.__PVT__psumReg 
                                          >> 0x17U)
                                       : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_3.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_3.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_3.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_3.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_3.__PVT__psumReg 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_3.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_3.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_3.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
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
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_3.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__io_validOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3__3(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3__3\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4__0\n"); );
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
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_4.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__io_aOut_REG) 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum 
                    = (0x000001ffU & ((0x000000ffU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                          >> 0x17U)) 
                                      + (0x000000ffU 
                                         & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                            >> 0x17U))));
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
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum) 
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
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_4.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax 
                    = (0x000000ffU & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                       ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_4.__PVT__psumReg 
                                          >> 0x17U)
                                       : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_4.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_4.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_4.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_4.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_4.__PVT__psumReg 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_4.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_4.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_4.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
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
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_4.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__io_validOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4__3(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4__3\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5__0\n"); );
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
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_5.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__io_aOut_REG) 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum 
                    = (0x000001ffU & ((0x000000ffU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                          >> 0x17U)) 
                                      + (0x000000ffU 
                                         & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                            >> 0x17U))));
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
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum) 
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
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_5.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax 
                    = (0x000000ffU & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                       ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_5.__PVT__psumReg 
                                          >> 0x17U)
                                       : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_5.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_5.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_5.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_5.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_5.__PVT__psumReg 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_5.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_5.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_5.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
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
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_5.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__io_validOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5__3(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5__3\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6__0\n"); );
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
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_6.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__io_aOut_REG) 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum 
                    = (0x000001ffU & ((0x000000ffU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                          >> 0x17U)) 
                                      + (0x000000ffU 
                                         & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                            >> 0x17U))));
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
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum) 
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
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_6.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax 
                    = (0x000000ffU & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                       ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_6.__PVT__psumReg 
                                          >> 0x17U)
                                       : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_6.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_6.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_6.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_6.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_6.__PVT__psumReg 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_6.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_6.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_6.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
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
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__io_rndOut_REG;
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_6.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__io_validOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_7__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_7__0\n"); );
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
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_7.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__io_aOut_REG) 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum 
                    = (0x000001ffU & ((0x000000ffU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                          >> 0x17U)) 
                                      + (0x000000ffU 
                                         & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                            >> 0x17U))));
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
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum) 
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
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_7.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax 
                    = (0x000000ffU & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                       ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_7.__PVT__psumReg 
                                          >> 0x17U)
                                       : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_7.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_7.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_7.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_7.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_7.__PVT__psumReg 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_7.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_7.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_7.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
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
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_5_7.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_7__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_7__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__io_validOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_7__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_7__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_0__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_0__0\n"); );
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
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_7.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_7) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_7) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_7)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP.svsimTestbench__DOT__io_aIn_7;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_7) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_7) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_7))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_7) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_7) 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum 
                    = (0x000001ffU & ((0x000000ffU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                          >> 0x17U)) 
                                      + (0x000000ffU 
                                         & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                            >> 0x17U))));
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
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum) 
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
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax 
                    = (0x000000ffU & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                       ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__psumReg 
                                          >> 0x17U)
                                       : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__psumReg 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
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
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_0.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP.svsimTestbench__DOT__io_fmtIn;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP.svsimTestbench__DOT__io_rndIn;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_0__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_0__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_7.__PVT__io_validOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_0__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_0__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP.svsimTestbench__DOT__io_aIn_7));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_1__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_1__0\n"); );
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
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_0.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_0.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_0.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_0.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_0.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_0.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_0.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_0.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_0.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_0.__PVT__io_aOut_REG) 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum 
                    = (0x000001ffU & ((0x000000ffU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                          >> 0x17U)) 
                                      + (0x000000ffU 
                                         & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                            >> 0x17U))));
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
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum) 
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
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax 
                    = (0x000000ffU & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                       ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__psumReg 
                                          >> 0x17U)
                                       : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__psumReg 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
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
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_1.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_0.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_0.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_1__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_1__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_0.__PVT__io_validOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_1__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_1__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_0.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_1__3(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_1__3\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_0.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_0.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_2__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_2__0\n"); );
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
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_1.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_1.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_1.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_1.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_1.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_1.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_1.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_1.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_1.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_1.__PVT__io_aOut_REG) 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum 
                    = (0x000001ffU & ((0x000000ffU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                          >> 0x17U)) 
                                      + (0x000000ffU 
                                         & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                            >> 0x17U))));
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
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum) 
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
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax 
                    = (0x000000ffU & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                       ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__psumReg 
                                          >> 0x17U)
                                       : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__psumReg 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
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
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_2.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_1.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_1.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_2__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_2__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_1.__PVT__io_validOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_2__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_2__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_1.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_2__3(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_2__3\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_1.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_1.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_3__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_3__0\n"); );
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
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_2.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_2.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_2.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_2.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_2.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_2.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_2.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_2.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_2.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_2.__PVT__io_aOut_REG) 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum 
                    = (0x000001ffU & ((0x000000ffU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                          >> 0x17U)) 
                                      + (0x000000ffU 
                                         & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                            >> 0x17U))));
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
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum) 
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
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax 
                    = (0x000000ffU & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                       ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__psumReg 
                                          >> 0x17U)
                                       : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__psumReg 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
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
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_3.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_2.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_2.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_3__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_3__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_2.__PVT__io_validOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_3__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_3__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_2.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_3__3(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_3__3\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_2.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_2.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_4__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_4__0\n"); );
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
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_3.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_3.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_3.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_3.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_3.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_3.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_3.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_3.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_3.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_3.__PVT__io_aOut_REG) 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum 
                    = (0x000001ffU & ((0x000000ffU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                          >> 0x17U)) 
                                      + (0x000000ffU 
                                         & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                            >> 0x17U))));
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
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum) 
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
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax 
                    = (0x000000ffU & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                       ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__psumReg 
                                          >> 0x17U)
                                       : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__psumReg 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
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
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_4.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_3.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_3.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_4__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_4__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_3.__PVT__io_validOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_4__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_4__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_3.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_4__3(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_4__3\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_3.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_3.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_5__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_5__0\n"); );
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
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_4.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_4.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_4.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_4.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_4.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_4.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_4.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_4.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_4.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_4.__PVT__io_aOut_REG) 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum 
                    = (0x000001ffU & ((0x000000ffU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                          >> 0x17U)) 
                                      + (0x000000ffU 
                                         & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                            >> 0x17U))));
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
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum) 
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
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax 
                    = (0x000000ffU & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                       ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__psumReg 
                                          >> 0x17U)
                                       : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__psumReg 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
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
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_5.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_4.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_4.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_5__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_5__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_4.__PVT__io_validOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_5__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_5__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_4.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_5__3(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_5__3\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_4.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_4.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_6__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_6__0\n"); );
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
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_5.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_5.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_5.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_5.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_5.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_5.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_5.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_5.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_5.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_5.__PVT__io_aOut_REG) 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum 
                    = (0x000001ffU & ((0x000000ffU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                          >> 0x17U)) 
                                      + (0x000000ffU 
                                         & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                            >> 0x17U))));
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
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum) 
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
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax 
                    = (0x000000ffU & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                       ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__psumReg 
                                          >> 0x17U)
                                       : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__psumReg 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
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
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_6.__PVT__psumReg);
        }
    }
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_5.__PVT__io_validOut_REG));
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_5.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_5.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_6__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_6__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_5.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_6__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_6__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_5.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_5.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_7__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_7__0\n"); );
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
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_7.__PVT__psumReg;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_6.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_6.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_6.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_6.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_6.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_6.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_6.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_6.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_6.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_6.__PVT__io_aOut_REG) 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum 
                    = (0x000001ffU & ((0x000000ffU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                          >> 0x17U)) 
                                      + (0x000000ffU 
                                         & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                            >> 0x17U))));
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
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum) 
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
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_7.__PVT__psumReg 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax 
                    = (0x000000ffU & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                       ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_7.__PVT__psumReg 
                                          >> 0x17U)
                                       : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_7.__PVT__psumReg 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_7.__PVT__psumReg 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_7.__PVT__psumReg 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_7.__PVT__psumReg 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_7.__PVT__psumReg 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_7.__PVT__psumReg 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_7.__PVT__psumReg)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_7.__PVT__psumReg
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
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
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_6_7.__PVT__psumReg);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_6.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_6.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_7__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_7__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_6.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_7__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_7__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_6.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_6.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_0__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_0__0\n"); );
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
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_0;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_0_7.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_0_7.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_0_7.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_0_7.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_0_7.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_0_7.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_0_7.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_0_7.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_0_7.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_0_7.__PVT__io_aOut_REG) 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum 
                    = (0x000001ffU & ((0x000000ffU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                          >> 0x17U)) 
                                      + (0x000000ffU 
                                         & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                            >> 0x17U))));
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
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum) 
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
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_0 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax 
                    = (0x000000ffU & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                       ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_0 
                                          >> 0x17U)
                                       : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_0 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_0 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_0 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_0 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_0 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_0 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_0)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_0
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
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
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_0);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_7.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_7.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_0__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_0__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_aOut_REG = 0U;
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_aOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_0_7.__PVT__io_aOut_REG;
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_7.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_7.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_1__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_1__0\n"); );
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
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_1;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_0.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_0.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_0.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_0.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_0.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_0.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_0.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_0.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_0.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_0.__PVT__io_aOut_REG) 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum 
                    = (0x000001ffU & ((0x000000ffU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                          >> 0x17U)) 
                                      + (0x000000ffU 
                                         & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                            >> 0x17U))));
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
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum) 
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
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_1 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax 
                    = (0x000000ffU & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                       ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_1 
                                          >> 0x17U)
                                       : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_1 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_1 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_1 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_1 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_1 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_1 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_1)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_1
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
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
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_1);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_0.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_0.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_1__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_1__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_aOut_REG = 0U;
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_aOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_0.__PVT__io_aOut_REG;
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_0.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_0.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_1__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_1__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_0.__PVT__io_validOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_2__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_2__0\n"); );
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
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_2;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_1.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_1.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_1.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_1.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_1.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_1.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_1.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_1.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_1.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_1.__PVT__io_aOut_REG) 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum 
                    = (0x000001ffU & ((0x000000ffU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                          >> 0x17U)) 
                                      + (0x000000ffU 
                                         & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                            >> 0x17U))));
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
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum) 
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
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_2 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax 
                    = (0x000000ffU & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                       ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_2 
                                          >> 0x17U)
                                       : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_2 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_2 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_2 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_2 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_2 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_2 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_2)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_2
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
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
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_2);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_1.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_1.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_2__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_2__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_aOut_REG = 0U;
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_aOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_1.__PVT__io_aOut_REG;
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_1.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_1.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_2__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_2__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_1.__PVT__io_validOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_3__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_3__0\n"); );
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
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_3;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_2.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_2.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_2.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_2.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_2.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_2.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_2.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_2.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_2.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_2.__PVT__io_aOut_REG) 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum 
                    = (0x000001ffU & ((0x000000ffU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                          >> 0x17U)) 
                                      + (0x000000ffU 
                                         & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                            >> 0x17U))));
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
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum) 
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
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_3 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax 
                    = (0x000000ffU & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                       ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_3 
                                          >> 0x17U)
                                       : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_3 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_3 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_3 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_3 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_3 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_3 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_3)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_3
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
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
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_3);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_2.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_2.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_3__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_3__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_aOut_REG = 0U;
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_aOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_2.__PVT__io_aOut_REG;
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_2.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_2.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_3__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_3__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_2.__PVT__io_validOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_4__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_4__0\n"); );
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
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_4;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_3.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_3.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_3.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_3.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_3.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_3.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_3.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_3.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_3.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_3.__PVT__io_aOut_REG) 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum 
                    = (0x000001ffU & ((0x000000ffU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                          >> 0x17U)) 
                                      + (0x000000ffU 
                                         & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                            >> 0x17U))));
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
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum) 
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
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_4 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax 
                    = (0x000000ffU & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                       ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_4 
                                          >> 0x17U)
                                       : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_4 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_4 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_4 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_4 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_4 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_4 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_4)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_4
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
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
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_4);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_3.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_3.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_4__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_4__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_aOut_REG = 0U;
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_aOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_3.__PVT__io_aOut_REG;
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_3.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_3.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_4__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_4__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_3.__PVT__io_validOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_5__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_5__0\n"); );
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
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_5;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_4.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_4.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_4.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_4.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_4.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_4.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_4.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_4.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_4.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_4.__PVT__io_aOut_REG) 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum 
                    = (0x000001ffU & ((0x000000ffU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                          >> 0x17U)) 
                                      + (0x000000ffU 
                                         & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                            >> 0x17U))));
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
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum) 
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
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_5 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax 
                    = (0x000000ffU & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                       ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_5 
                                          >> 0x17U)
                                       : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_5 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_5 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_5 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_5 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_5 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_5 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_5)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_5
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
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
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_5);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_4.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_4.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_5__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_5__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__io_aOut_REG = 0U;
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
    } else {
        vlSelfRef.__PVT__io_aOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_4.__PVT__io_aOut_REG;
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_4.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_4.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_5__2(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_5__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_4.__PVT__io_validOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_6__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_6__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (vlSymsp->TOP.svsimTestbench__DOT__reset) {
        vlSelfRef.__PVT__psumReg = 0U;
        vlSelfRef.__PVT__io_aOut_REG = 0U;
        vlSelfRef.__PVT__io_fmtOut_REG = 3U;
        vlSelfRef.__PVT__io_rndOut_REG = 0U;
        vlSelfRef.__PVT__wReg = 0U;
    } else {
        if (vlSymsp->TOP.svsimTestbench__DOT__io_clear) {
            vlSelfRef.__PVT__psumReg = 0U;
        } else if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_6;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_5.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_5.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_5.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_5.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_5.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_5.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_5.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_5.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_5.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_5.__PVT__io_aOut_REG) 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum 
                    = (0x000001ffU & ((0x000000ffU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                          >> 0x17U)) 
                                      + (0x000000ffU 
                                         & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                            >> 0x17U))));
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
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum) 
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
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_6 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax 
                    = (0x000000ffU & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                       ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_6 
                                          >> 0x17U)
                                       : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_6 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_6 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_6 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_6 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_6 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_6 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_6)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_6
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
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
        vlSelfRef.__PVT__io_aOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_5.__PVT__io_aOut_REG;
        vlSelfRef.__PVT__io_fmtOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_5.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__io_rndOut_REG = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_5.__PVT__io_rndOut_REG;
        if (vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_6);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_5.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_5.__PVT__io_rndOut_REG;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_6__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_6__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_validOut_REG = ((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__reset))) 
                                        && (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_5.__PVT__io_validOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_7__0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_7__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__PVT__io_aOut_REG = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__reset)
                                     ? 0U : (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_6.__PVT__io_aOut_REG));
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_7__1(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_7__1\n"); );
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
            vlSelfRef.__PVT__psumReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_7;
        } else {
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.__PVT__fmtReg));
            vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.__PVT__fmtReg));
            if (((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_6.__PVT__io_validOut_REG) 
                 & ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.__PVT__fmtReg))))) {
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_6.__PVT__io_aOut_REG) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.__PVT__fmtReg))) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_6.__PVT__io_aOut_REG) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_6.__PVT__io_aOut_REG)));
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.__PVT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.__PVT__wReg)));
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_6.__PVT__io_aOut_REG;
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.__PVT__wReg;
                }
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.__PVT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.__PVT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_6.__PVT__io_aOut_REG) 
                           << 0x00000010U);
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.__PVT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_6.__PVT__io_aOut_REG) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_6.__PVT__io_aOut_REG))))
                               ? 0U : ((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_6.__PVT__io_aOut_REG) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_6.__PVT__io_aOut_REG) 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum 
                    = (0x000001ffU & ((0x000000ffU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                          >> 0x17U)) 
                                      + (0x000000ffU 
                                         & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                            >> 0x17U))));
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
                                    | ((((0x007eU < (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_eSum) 
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
                    = ((0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_7 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax 
                    = (0x000000ffU & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                       ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_7 
                                          >> 0x17U)
                                       : (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          >> 0x17U)));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_7 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_7 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_7 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_7 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_7 
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
                vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.__PVT__psumReg = (((3U == (IData)(vlSelfRef.__PVT__fmtReg)) 
                                             | (IData)(vlSelfRef.__PVT__unnamedblk1__DOT___psumReg_isInt_T_1))
                                             ? (vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_7 
                                                + vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                                             : ((IData)(
                                                        (0U 
                                                         == 
                                                         (0x7fffffffU 
                                                          & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_7)))
                                                 ? vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product
                                                 : 
                                                ((IData)(
                                                         (0U 
                                                          == 
                                                          (0x7fffffffU 
                                                           & vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                                  ? vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_7
                                                  : 
                                                 ((0U 
                                                   == vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 0U
                                                   : 
                                                  ((0x80000000U 
                                                    & (vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                       << 5U)) 
                                                   | (((((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
                                                         > (IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                         ? 
                                                        (0x000000ffU 
                                                         & ((IData)(vlSelfRef.__PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_eMax) 
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
            vlSelfRef.__PVT__wReg = (0x0000ffffU & vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1.io_psumIn_7);
        }
    }
    if (vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.__PVT__fmtReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_6.__PVT__io_fmtOut_REG;
        vlSelfRef.__PVT__rndReg = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_1__pes_0_6.__PVT__io_rndOut_REG;
    }
}
