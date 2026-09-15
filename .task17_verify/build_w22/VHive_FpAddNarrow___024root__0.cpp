// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See VHive_FpAddNarrow.h for the primary calling header

#include "VHive_FpAddNarrow__pch.h"

bool VHive_FpAddNarrow___024root___trigger_anySet__ico(const VlUnpacked<QData/*63:0*/, 2> &in) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VHive_FpAddNarrow___024root___trigger_anySet__ico\n"); );
    // Locals
    IData/*31:0*/ n;
    // Body
    n = 0U;
    do {
        if (in[n]) {
            return (1U);
        }
        n = ((IData)(1U) + n);
    } while ((2U > n));
    return (0U);
}

void VHive_FpAddNarrow___024root___ico_comb__TOP__6(VHive_FpAddNarrow___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VHive_FpAddNarrow___024root___ico_comb__TOP__6\n"); );
    VHive_FpAddNarrow__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    SData/*8:0*/ Hive_FpAddNarrow__DOT__E;
    Hive_FpAddNarrow__DOT__E = 0;
    SData/*8:0*/ Hive_FpAddNarrow__DOT___aC_sRaw_T;
    Hive_FpAddNarrow__DOT___aC_sRaw_T = 0;
    SData/*8:0*/ Hive_FpAddNarrow__DOT___a0_sRaw_T;
    Hive_FpAddNarrow__DOT___a0_sRaw_T = 0;
    SData/*8:0*/ Hive_FpAddNarrow__DOT___a1_sRaw_T;
    Hive_FpAddNarrow__DOT___a1_sRaw_T = 0;
    SData/*8:0*/ Hive_FpAddNarrow__DOT___a2_sRaw_T;
    Hive_FpAddNarrow__DOT___a2_sRaw_T = 0;
    SData/*8:0*/ Hive_FpAddNarrow__DOT___a3_sRaw_T;
    Hive_FpAddNarrow__DOT___a3_sRaw_T = 0;
    IData/*22:0*/ Hive_FpAddNarrow__DOT___GEN;
    Hive_FpAddNarrow__DOT___GEN = 0;
    IData/*22:0*/ Hive_FpAddNarrow__DOT__fC;
    Hive_FpAddNarrow__DOT__fC = 0;
    IData/*22:0*/ Hive_FpAddNarrow__DOT___GEN_0;
    Hive_FpAddNarrow__DOT___GEN_0 = 0;
    IData/*22:0*/ Hive_FpAddNarrow__DOT__f0;
    Hive_FpAddNarrow__DOT__f0 = 0;
    IData/*22:0*/ Hive_FpAddNarrow__DOT___GEN_1;
    Hive_FpAddNarrow__DOT___GEN_1 = 0;
    IData/*22:0*/ Hive_FpAddNarrow__DOT__f1;
    Hive_FpAddNarrow__DOT__f1 = 0;
    IData/*22:0*/ Hive_FpAddNarrow__DOT___GEN_2;
    Hive_FpAddNarrow__DOT___GEN_2 = 0;
    IData/*22:0*/ Hive_FpAddNarrow__DOT__f2;
    Hive_FpAddNarrow__DOT__f2 = 0;
    IData/*22:0*/ Hive_FpAddNarrow__DOT___GEN_3;
    Hive_FpAddNarrow__DOT___GEN_3 = 0;
    IData/*22:0*/ Hive_FpAddNarrow__DOT__f3;
    Hive_FpAddNarrow__DOT__f3 = 0;
    IData/*23:0*/ Hive_FpAddNarrow__DOT__tA;
    Hive_FpAddNarrow__DOT__tA = 0;
    IData/*23:0*/ Hive_FpAddNarrow__DOT__tB;
    Hive_FpAddNarrow__DOT__tB = 0;
    IData/*24:0*/ Hive_FpAddNarrow__DOT__tC;
    Hive_FpAddNarrow__DOT__tC = 0;
    IData/*25:0*/ Hive_FpAddNarrow__DOT__S;
    Hive_FpAddNarrow__DOT__S = 0;
    IData/*24:0*/ Hive_FpAddNarrow__DOT___magFull_T_3;
    Hive_FpAddNarrow__DOT___magFull_T_3 = 0;
    CData/*1:0*/ Hive_FpAddNarrow__DOT__kGrow;
    Hive_FpAddNarrow__DOT__kGrow = 0;
    SData/*9:0*/ Hive_FpAddNarrow__DOT__expRaw;
    Hive_FpAddNarrow__DOT__expRaw = 0;
    // Body
    Hive_FpAddNarrow__DOT__E = (((IData)(vlSelfRef.Hive_FpAddNarrow__DOT__eMaxA) 
                                 >= (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__e3))
                                 ? (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__eMaxA)
                                 : (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__e3));
    Hive_FpAddNarrow__DOT___a3_sRaw_T = (0x000001ffU 
                                         & ((IData)(Hive_FpAddNarrow__DOT__E) 
                                            - (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__e3)));
    Hive_FpAddNarrow__DOT___aC_sRaw_T = (0x000001ffU 
                                         & ((IData)(Hive_FpAddNarrow__DOT__E) 
                                            - (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__eC)));
    Hive_FpAddNarrow__DOT___a0_sRaw_T = (0x000001ffU 
                                         & ((IData)(Hive_FpAddNarrow__DOT__E) 
                                            - (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__e0)));
    Hive_FpAddNarrow__DOT___a1_sRaw_T = (0x000001ffU 
                                         & ((IData)(Hive_FpAddNarrow__DOT__E) 
                                            - (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__e1)));
    Hive_FpAddNarrow__DOT___a2_sRaw_T = (0x000001ffU 
                                         & ((IData)(Hive_FpAddNarrow__DOT__E) 
                                            - (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__e2)));
    Hive_FpAddNarrow__DOT___GEN_3 = (((0x00200000U 
                                       | (0x001fffffU 
                                          & (vlSelfRef.io_prodFp4_1 
                                             >> 2U))) 
                                      & (- (IData)((IData)(vlSelfRef.Hive_FpAddNarrow__DOT__valid_3)))) 
                                     >> ((0x0016U < (IData)(Hive_FpAddNarrow__DOT___a3_sRaw_T))
                                          ? 0x00000016U
                                          : (0x0000001fU 
                                             & (IData)(Hive_FpAddNarrow__DOT___a3_sRaw_T))));
    Hive_FpAddNarrow__DOT___GEN = ((0x003fffffU & ((IData)(
                                                           (vlSelfRef.io_cReg 
                                                            >> 9U)) 
                                                   & (- (IData)((IData)(vlSelfRef.__VdfgRegularize_hebeb780c_0_1))))) 
                                   >> ((0x0016U < (IData)(Hive_FpAddNarrow__DOT___aC_sRaw_T))
                                        ? 0x00000016U
                                        : (0x0000001fU 
                                           & (IData)(Hive_FpAddNarrow__DOT___aC_sRaw_T))));
    Hive_FpAddNarrow__DOT___GEN_0 = (((0x00200000U 
                                       | (0x001fffffU 
                                          & (vlSelfRef.io_prodFp16 
                                             >> 2U))) 
                                      & (- (IData)((IData)(vlSelfRef.__VdfgRegularize_hebeb780c_0_0)))) 
                                     >> ((0x0016U < (IData)(Hive_FpAddNarrow__DOT___a0_sRaw_T))
                                          ? 0x00000016U
                                          : (0x0000001fU 
                                             & (IData)(Hive_FpAddNarrow__DOT___a0_sRaw_T))));
    Hive_FpAddNarrow__DOT___GEN_1 = (((0x00200000U 
                                       | (0x001fffffU 
                                          & (vlSelfRef.io_prodFp8 
                                             >> 2U))) 
                                      & (- (IData)((IData)(vlSelfRef.Hive_FpAddNarrow__DOT__valid_1)))) 
                                     >> ((0x0016U < (IData)(Hive_FpAddNarrow__DOT___a1_sRaw_T))
                                          ? 0x00000016U
                                          : (0x0000001fU 
                                             & (IData)(Hive_FpAddNarrow__DOT___a1_sRaw_T))));
    Hive_FpAddNarrow__DOT___GEN_2 = (((0x00200000U 
                                       | (0x001fffffU 
                                          & (vlSelfRef.io_prodFp4_0 
                                             >> 2U))) 
                                      & (- (IData)((IData)(vlSelfRef.Hive_FpAddNarrow__DOT__valid_2)))) 
                                     >> ((0x0016U < (IData)(Hive_FpAddNarrow__DOT___a2_sRaw_T))
                                          ? 0x00000016U
                                          : (0x0000001fU 
                                             & (IData)(Hive_FpAddNarrow__DOT___a2_sRaw_T))));
    Hive_FpAddNarrow__DOT__f3 = (0x007fffffU & ((vlSelfRef.io_prodFp4_1 
                                                 >> 0x0000001fU)
                                                 ? 
                                                (- Hive_FpAddNarrow__DOT___GEN_3)
                                                 : Hive_FpAddNarrow__DOT___GEN_3));
    Hive_FpAddNarrow__DOT__fC = (0x007fffffU & ((1U 
                                                 & (IData)(
                                                           (vlSelfRef.io_cReg 
                                                            >> 0x00000027U)))
                                                 ? 
                                                (- Hive_FpAddNarrow__DOT___GEN)
                                                 : Hive_FpAddNarrow__DOT___GEN));
    Hive_FpAddNarrow__DOT__f0 = (0x007fffffU & ((vlSelfRef.io_prodFp16 
                                                 >> 0x0000001fU)
                                                 ? 
                                                (- Hive_FpAddNarrow__DOT___GEN_0)
                                                 : Hive_FpAddNarrow__DOT___GEN_0));
    Hive_FpAddNarrow__DOT__f1 = (0x007fffffU & ((vlSelfRef.io_prodFp8 
                                                 >> 0x0000001fU)
                                                 ? 
                                                (- Hive_FpAddNarrow__DOT___GEN_1)
                                                 : Hive_FpAddNarrow__DOT___GEN_1));
    Hive_FpAddNarrow__DOT__f2 = (0x007fffffU & ((vlSelfRef.io_prodFp4_0 
                                                 >> 0x0000001fU)
                                                 ? 
                                                (- Hive_FpAddNarrow__DOT___GEN_2)
                                                 : Hive_FpAddNarrow__DOT___GEN_2));
    Hive_FpAddNarrow__DOT__tA = (0x00ffffffU & (((0x00800000U 
                                                  & (Hive_FpAddNarrow__DOT__fC 
                                                     << 1U)) 
                                                 | Hive_FpAddNarrow__DOT__fC) 
                                                + (
                                                   (0x00800000U 
                                                    & (Hive_FpAddNarrow__DOT__f0 
                                                       << 1U)) 
                                                   | Hive_FpAddNarrow__DOT__f0)));
    Hive_FpAddNarrow__DOT__tB = (0x00ffffffU & (((0x00800000U 
                                                  & (Hive_FpAddNarrow__DOT__f1 
                                                     << 1U)) 
                                                 | Hive_FpAddNarrow__DOT__f1) 
                                                + (
                                                   (0x00800000U 
                                                    & (Hive_FpAddNarrow__DOT__f2 
                                                       << 1U)) 
                                                   | Hive_FpAddNarrow__DOT__f2)));
    Hive_FpAddNarrow__DOT__tC = (0x01ffffffU & (((0x01000000U 
                                                  & (Hive_FpAddNarrow__DOT__tA 
                                                     << 1U)) 
                                                 | Hive_FpAddNarrow__DOT__tA) 
                                                + (
                                                   (0x01000000U 
                                                    & (Hive_FpAddNarrow__DOT__tB 
                                                       << 1U)) 
                                                   | Hive_FpAddNarrow__DOT__tB)));
    Hive_FpAddNarrow__DOT__S = (0x03ffffffU & (((0x02000000U 
                                                 & (Hive_FpAddNarrow__DOT__tC 
                                                    << 1U)) 
                                                | Hive_FpAddNarrow__DOT__tC) 
                                               + ((0x03800000U 
                                                   & ((- (IData)(
                                                                 (1U 
                                                                  & (Hive_FpAddNarrow__DOT__f3 
                                                                     >> 0x00000016U)))) 
                                                      << 0x00000017U)) 
                                                  | Hive_FpAddNarrow__DOT__f3)));
    Hive_FpAddNarrow__DOT___magFull_T_3 = (0x01ffffffU 
                                           & (VL_GTS_III(26, 0U, Hive_FpAddNarrow__DOT__S)
                                               ? (- Hive_FpAddNarrow__DOT__S)
                                               : Hive_FpAddNarrow__DOT__S));
    Hive_FpAddNarrow__DOT__kGrow = (3U & (((0x00800000U 
                                            & Hive_FpAddNarrow__DOT___magFull_T_3)
                                            ? 2U : 
                                           (1U & (Hive_FpAddNarrow__DOT___magFull_T_3 
                                                  >> 0x00000016U))) 
                                          | (- (IData)(
                                                       (1U 
                                                        & (Hive_FpAddNarrow__DOT___magFull_T_3 
                                                           >> 0x00000018U))))));
    Hive_FpAddNarrow__DOT__expRaw = (0x000003ffU & 
                                     ((IData)(Hive_FpAddNarrow__DOT__kGrow) 
                                      + (IData)(Hive_FpAddNarrow__DOT__E)));
    vlSelfRef.io_sum = ((((QData)((IData)(((VL_GTS_III(26, 0U, Hive_FpAddNarrow__DOT__S) 
                                            << 8U) 
                                           | (0x000000ffU 
                                              & ((IData)(Hive_FpAddNarrow__DOT__expRaw) 
                                                 | (- (IData)(
                                                              (0U 
                                                               != 
                                                               (3U 
                                                                & ((IData)(Hive_FpAddNarrow__DOT__expRaw) 
                                                                   >> 8U)))))))))) 
                          << 0x0000001fU) | (QData)((IData)(
                                                            (0x7ffffe00U 
                                                             & ((Hive_FpAddNarrow__DOT___magFull_T_3 
                                                                 >> (IData)(Hive_FpAddNarrow__DOT__kGrow)) 
                                                                << 9U))))) 
                        & (- (QData)((IData)((0U != Hive_FpAddNarrow__DOT___magFull_T_3)))));
}

void VHive_FpAddNarrow___024root___eval_ico(VHive_FpAddNarrow___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VHive_FpAddNarrow___024root___eval_ico\n"); );
    VHive_FpAddNarrow__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if ((0x0000000000000104ULL & vlSelfRef.__VicoTriggered[0U])) {
        {
            // Inlined CFunc: _ico_comb__TOP__0
            vlSelfRef.Hive_FpAddNarrow__DOT__valid_3 
                = ((~ (IData)((0U == (0x7fffffffU & vlSelfRef.io_prodFp4_1)))) 
                   & (IData)(vlSelfRef.io_is4x));
            vlSelfRef.Hive_FpAddNarrow__DOT__e3 = (0x000000ffU 
                                                   & ((- (IData)((IData)(vlSelfRef.Hive_FpAddNarrow__DOT__valid_3))) 
                                                      & (vlSelfRef.io_prodFp4_1 
                                                         >> 0x00000017U)));
        }
    }
    if ((0x0000000000000020ULL & vlSelfRef.__VicoTriggered[0U])) {
        {
            // Inlined CFunc: _ico_sequent__TOP__0
            vlSelfRef.__VdfgRegularize_hebeb780c_0_0 
                = (1U & (~ (IData)((0U == (0x7fffffffU 
                                           & vlSelfRef.io_prodFp16)))));
            vlSelfRef.Hive_FpAddNarrow__DOT__e0 = (0x000000ffU 
                                                   & ((- (IData)((IData)(vlSelfRef.__VdfgRegularize_hebeb780c_0_0))) 
                                                      & (vlSelfRef.io_prodFp16 
                                                         >> 0x00000017U)));
        }
    }
    if ((0x0000000000000010ULL & vlSelfRef.__VicoTriggered[0U])) {
        {
            // Inlined CFunc: _ico_sequent__TOP__1
            vlSelfRef.__VdfgRegularize_hebeb780c_0_1 
                = (1U & (~ (IData)((0ULL == (0x0000007fffffffffULL 
                                             & vlSelfRef.io_cReg)))));
            vlSelfRef.Hive_FpAddNarrow__DOT__eC = (0x000000ffU 
                                                   & ((- (IData)((IData)(vlSelfRef.__VdfgRegularize_hebeb780c_0_1))) 
                                                      & (IData)(
                                                                (vlSelfRef.io_cReg 
                                                                 >> 0x0000001fU))));
        }
    }
    if ((0x0000000000000084ULL & vlSelfRef.__VicoTriggered[0U])) {
        {
            // Inlined CFunc: _ico_comb__TOP__1
            vlSelfRef.Hive_FpAddNarrow__DOT__valid_2 
                = ((~ (IData)((0U == (0x7fffffffU & vlSelfRef.io_prodFp4_0)))) 
                   & (IData)(vlSelfRef.io_is4x));
            vlSelfRef.Hive_FpAddNarrow__DOT__e2 = (0x000000ffU 
                                                   & ((- (IData)((IData)(vlSelfRef.Hive_FpAddNarrow__DOT__valid_2))) 
                                                      & (vlSelfRef.io_prodFp4_0 
                                                         >> 0x00000017U)));
        }
    }
    if ((0x000000000000004cULL & vlSelfRef.__VicoTriggered[0U])) {
        {
            // Inlined CFunc: _ico_comb__TOP__2
            vlSelfRef.Hive_FpAddNarrow__DOT__valid_1 
                = ((~ (IData)((0U == (0x7fffffffU & vlSelfRef.io_prodFp8)))) 
                   & ((IData)(vlSelfRef.io_is4x) | (IData)(vlSelfRef.io_is2x)));
            vlSelfRef.Hive_FpAddNarrow__DOT__e1 = (0x000000ffU 
                                                   & ((- (IData)((IData)(vlSelfRef.Hive_FpAddNarrow__DOT__valid_1))) 
                                                      & (vlSelfRef.io_prodFp8 
                                                         >> 0x00000017U)));
        }
    }
    if ((0x0000000000000030ULL & vlSelfRef.__VicoTriggered[0U])) {
        {
            // Inlined CFunc: _ico_comb__TOP__3
            vlSelfRef.Hive_FpAddNarrow__DOT__eMaxC0 
                = (((IData)(vlSelfRef.Hive_FpAddNarrow__DOT__eC) 
                    >= (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__e0))
                    ? (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__eC)
                    : (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__e0));
        }
    }
    if ((0x00000000000000ccULL & vlSelfRef.__VicoTriggered[0U])) {
        {
            // Inlined CFunc: _ico_comb__TOP__4
            vlSelfRef.Hive_FpAddNarrow__DOT__eMax12 
                = (((IData)(vlSelfRef.Hive_FpAddNarrow__DOT__e1) 
                    >= (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__e2))
                    ? (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__e1)
                    : (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__e2));
        }
    }
    if ((0x00000000000000fcULL & vlSelfRef.__VicoTriggered[0U])) {
        {
            // Inlined CFunc: _ico_comb__TOP__5
            vlSelfRef.Hive_FpAddNarrow__DOT__eMaxA 
                = (((IData)(vlSelfRef.Hive_FpAddNarrow__DOT__eMaxC0) 
                    >= (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__eMax12))
                    ? (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__eMaxC0)
                    : (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__eMax12));
        }
    }
    if ((0x00000000000001fcULL & vlSelfRef.__VicoTriggered[0U])) {
        VHive_FpAddNarrow___024root___ico_comb__TOP__6(vlSelf);
    }
}

#ifdef VL_DEBUG
VL_ATTR_COLD void VHive_FpAddNarrow___024root___dump_triggers__ico(const VlUnpacked<QData/*63:0*/, 2> &triggers, const std::string &tag);
#endif  // VL_DEBUG

bool VHive_FpAddNarrow___024root___eval_phase__ico(VHive_FpAddNarrow___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VHive_FpAddNarrow___024root___eval_phase__ico\n"); );
    VHive_FpAddNarrow__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    CData/*0:0*/ __VicoExecute;
    // Body
    {
        // Inlined CFunc: _eval_triggers_vec__ico
        vlSelfRef.__VicoTriggered[0U] = (QData)((IData)(
                                                        (((vlSelfRef.io_prodFp4_1 
                                                           != vlSelfRef.__Vtrigprevexpr___TOP__io_prodFp4_1__0) 
                                                          << 8U) 
                                                         | ((((((vlSelfRef.io_prodFp4_0 
                                                                 != vlSelfRef.__Vtrigprevexpr___TOP__io_prodFp4_0__0) 
                                                                << 3U) 
                                                               | ((vlSelfRef.io_prodFp8 
                                                                   != vlSelfRef.__Vtrigprevexpr___TOP__io_prodFp8__0) 
                                                                  << 2U)) 
                                                              | (((vlSelfRef.io_prodFp16 
                                                                   != vlSelfRef.__Vtrigprevexpr___TOP__io_prodFp16__0) 
                                                                  << 1U) 
                                                                 | (vlSelfRef.io_cReg 
                                                                    != vlSelfRef.__Vtrigprevexpr___TOP__io_cReg__0))) 
                                                             << 4U) 
                                                            | (((((IData)(vlSelfRef.io_is2x) 
                                                                  != (IData)(vlSelfRef.__Vtrigprevexpr___TOP__io_is2x__0)) 
                                                                 << 3U) 
                                                                | (((IData)(vlSelfRef.io_is4x) 
                                                                    != (IData)(vlSelfRef.__Vtrigprevexpr___TOP__io_is4x__0)) 
                                                                   << 2U)) 
                                                               | ((((IData)(vlSelfRef.reset) 
                                                                    != (IData)(vlSelfRef.__Vtrigprevexpr___TOP__reset__0)) 
                                                                   << 1U) 
                                                                  | ((IData)(vlSelfRef.clock) 
                                                                     != (IData)(vlSelfRef.__Vtrigprevexpr___TOP__clock__0))))))));
        vlSelfRef.__Vtrigprevexpr___TOP__clock__0 = vlSelfRef.clock;
        vlSelfRef.__Vtrigprevexpr___TOP__reset__0 = vlSelfRef.reset;
        vlSelfRef.__Vtrigprevexpr___TOP__io_is4x__0 
            = vlSelfRef.io_is4x;
        vlSelfRef.__Vtrigprevexpr___TOP__io_is2x__0 
            = vlSelfRef.io_is2x;
        vlSelfRef.__Vtrigprevexpr___TOP__io_cReg__0 
            = vlSelfRef.io_cReg;
        vlSelfRef.__Vtrigprevexpr___TOP__io_prodFp16__0 
            = vlSelfRef.io_prodFp16;
        vlSelfRef.__Vtrigprevexpr___TOP__io_prodFp8__0 
            = vlSelfRef.io_prodFp8;
        vlSelfRef.__Vtrigprevexpr___TOP__io_prodFp4_0__0 
            = vlSelfRef.io_prodFp4_0;
        vlSelfRef.__Vtrigprevexpr___TOP__io_prodFp4_1__0 
            = vlSelfRef.io_prodFp4_1;
        if (VL_UNLIKELY(((1U & (~ (IData)(vlSelfRef.__VicoDidInit)))))) {
            vlSelfRef.__VicoDidInit = 1U;
            vlSelfRef.__VicoTriggered[0U] = (1ULL | vlSelfRef.__VicoTriggered[0U]);
            vlSelfRef.__VicoTriggered[0U] = (2ULL | vlSelfRef.__VicoTriggered[0U]);
            vlSelfRef.__VicoTriggered[0U] = (4ULL | vlSelfRef.__VicoTriggered[0U]);
            vlSelfRef.__VicoTriggered[0U] = (8ULL | vlSelfRef.__VicoTriggered[0U]);
            vlSelfRef.__VicoTriggered[0U] = (0x0000000000000010ULL 
                                             | vlSelfRef.__VicoTriggered[0U]);
            vlSelfRef.__VicoTriggered[0U] = (0x0000000000000020ULL 
                                             | vlSelfRef.__VicoTriggered[0U]);
            vlSelfRef.__VicoTriggered[0U] = (0x0000000000000040ULL 
                                             | vlSelfRef.__VicoTriggered[0U]);
            vlSelfRef.__VicoTriggered[0U] = (0x0000000000000080ULL 
                                             | vlSelfRef.__VicoTriggered[0U]);
            vlSelfRef.__VicoTriggered[0U] = (0x0000000000000100ULL 
                                             | vlSelfRef.__VicoTriggered[0U]);
        }
    }
#ifdef VL_DEBUG
    if (VL_UNLIKELY(vlSymsp->_vm_contextp__->debug())) {
        VHive_FpAddNarrow___024root___dump_triggers__ico(vlSelfRef.__VicoTriggered, "ico"s);
    }
#endif
    __VicoExecute = VHive_FpAddNarrow___024root___trigger_anySet__ico(vlSelfRef.__VicoTriggered);
    if (__VicoExecute) {
        VHive_FpAddNarrow___024root___eval_ico(vlSelf);
    }
    return (__VicoExecute);
}

void VHive_FpAddNarrow___024root___eval(VHive_FpAddNarrow___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VHive_FpAddNarrow___024root___eval\n"); );
    VHive_FpAddNarrow__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    IData/*31:0*/ __VicoIterCount;
    // Body
    __VicoIterCount = 0U;
    do {
        if (VL_UNLIKELY(((0x00002710U < __VicoIterCount)))) {
#ifdef VL_DEBUG
            VHive_FpAddNarrow___024root___dump_triggers__ico(vlSelfRef.__VicoTriggered, "ico"s);
#endif
            VL_FATAL_MT("../src/hive/verilog_narrow_w22/Hive_FpAddNarrow.sv", 2, "", "DIDNOTCONVERGE: Input combinational region did not converge after '--converge-limit' of 10000 tries");
        }
        __VicoIterCount = ((IData)(1U) + __VicoIterCount);
        vlSelfRef.__VicoPhaseResult = VHive_FpAddNarrow___024root___eval_phase__ico(vlSelf);
    } while (vlSelfRef.__VicoPhaseResult);
}

#ifdef VL_DEBUG
void VHive_FpAddNarrow___024root___eval_debug_assertions(VHive_FpAddNarrow___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VHive_FpAddNarrow___024root___eval_debug_assertions\n"); );
    VHive_FpAddNarrow__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if (VL_UNLIKELY(((vlSelfRef.clock & 0xfeU)))) {
        Verilated::overWidthError("clock");
    }
    if (VL_UNLIKELY(((vlSelfRef.reset & 0xfeU)))) {
        Verilated::overWidthError("reset");
    }
    if (VL_UNLIKELY(((vlSelfRef.io_is4x & 0xfeU)))) {
        Verilated::overWidthError("io_is4x");
    }
    if (VL_UNLIKELY(((vlSelfRef.io_is2x & 0xfeU)))) {
        Verilated::overWidthError("io_is2x");
    }
    if (VL_UNLIKELY(((vlSelfRef.io_cReg & 0ULL)))) {
        Verilated::overWidthError("io_cReg");
    }
}
#endif  // VL_DEBUG
