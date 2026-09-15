// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See VHive_FpAddNarrow.h for the primary calling header

#include "VHive_FpAddNarrow__pch.h"

VL_ATTR_COLD void VHive_FpAddNarrow___024root___eval_static(VHive_FpAddNarrow___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VHive_FpAddNarrow___024root___eval_static\n"); );
    VHive_FpAddNarrow__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.__Vtrigprevexpr___TOP__clock__0 = vlSelfRef.clock;
    vlSelfRef.__Vtrigprevexpr___TOP__reset__0 = vlSelfRef.reset;
    vlSelfRef.__Vtrigprevexpr___TOP__io_is4x__0 = vlSelfRef.io_is4x;
    vlSelfRef.__Vtrigprevexpr___TOP__io_is2x__0 = vlSelfRef.io_is2x;
    vlSelfRef.__Vtrigprevexpr___TOP__io_cReg__0 = vlSelfRef.io_cReg;
    vlSelfRef.__Vtrigprevexpr___TOP__io_prodFp16__0 
        = vlSelfRef.io_prodFp16;
    vlSelfRef.__Vtrigprevexpr___TOP__io_prodFp8__0 
        = vlSelfRef.io_prodFp8;
    vlSelfRef.__Vtrigprevexpr___TOP__io_prodFp4_0__0 
        = vlSelfRef.io_prodFp4_0;
    vlSelfRef.__Vtrigprevexpr___TOP__io_prodFp4_1__0 
        = vlSelfRef.io_prodFp4_1;
}

VL_ATTR_COLD void VHive_FpAddNarrow___024root___eval_initial(VHive_FpAddNarrow___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VHive_FpAddNarrow___024root___eval_initial\n"); );
    VHive_FpAddNarrow__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
}

VL_ATTR_COLD void VHive_FpAddNarrow___024root___eval_final(VHive_FpAddNarrow___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VHive_FpAddNarrow___024root___eval_final\n"); );
    VHive_FpAddNarrow__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
}

#ifdef VL_DEBUG
VL_ATTR_COLD void VHive_FpAddNarrow___024root___dump_triggers__stl(const VlUnpacked<QData/*63:0*/, 1> &triggers, const std::string &tag);
#endif  // VL_DEBUG
VL_ATTR_COLD bool VHive_FpAddNarrow___024root___eval_phase__stl(VHive_FpAddNarrow___024root* vlSelf);

VL_ATTR_COLD void VHive_FpAddNarrow___024root___eval_settle(VHive_FpAddNarrow___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VHive_FpAddNarrow___024root___eval_settle\n"); );
    VHive_FpAddNarrow__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    IData/*31:0*/ __VstlIterCount;
    // Body
    __VstlIterCount = 0U;
    vlSelfRef.__VstlFirstIteration = 1U;
    do {
        if (VL_UNLIKELY(((0x00002710U < __VstlIterCount)))) {
#ifdef VL_DEBUG
            VHive_FpAddNarrow___024root___dump_triggers__stl(vlSelfRef.__VstlTriggered, "stl"s);
#endif
            VL_FATAL_MT("../src/hive/verilog_narrow_w22/Hive_FpAddNarrow.sv", 2, "", "DIDNOTCONVERGE: Settle region did not converge after '--converge-limit' of 10000 tries");
        }
        __VstlIterCount = ((IData)(1U) + __VstlIterCount);
        vlSelfRef.__VstlPhaseResult = VHive_FpAddNarrow___024root___eval_phase__stl(vlSelf);
        vlSelfRef.__VstlFirstIteration = 0U;
    } while (vlSelfRef.__VstlPhaseResult);
}

VL_ATTR_COLD bool VHive_FpAddNarrow___024root___trigger_anySet__stl(const VlUnpacked<QData/*63:0*/, 1> &in);

#ifdef VL_DEBUG
VL_ATTR_COLD void VHive_FpAddNarrow___024root___dump_triggers__stl(const VlUnpacked<QData/*63:0*/, 1> &triggers, const std::string &tag) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VHive_FpAddNarrow___024root___dump_triggers__stl\n"); );
    // Body
    if ((1U & (~ (IData)(VHive_FpAddNarrow___024root___trigger_anySet__stl(triggers))))) {
        VL_DBG_MSGS("         No '" + tag + "' region triggers active\n");
    }
    if ((1U & (IData)(triggers[0U]))) {
        VL_DBG_MSGS("         '" + tag + "' region trigger index 0 is active: Internal 'stl' trigger - first iteration\n");
    }
}
#endif  // VL_DEBUG

VL_ATTR_COLD bool VHive_FpAddNarrow___024root___trigger_anySet__stl(const VlUnpacked<QData/*63:0*/, 1> &in) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VHive_FpAddNarrow___024root___trigger_anySet__stl\n"); );
    // Locals
    IData/*31:0*/ n;
    // Body
    n = 0U;
    do {
        if (in[n]) {
            return (1U);
        }
        n = ((IData)(1U) + n);
    } while ((1U > n));
    return (0U);
}

VL_ATTR_COLD void VHive_FpAddNarrow___024root___stl_sequent__TOP__0(VHive_FpAddNarrow___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VHive_FpAddNarrow___024root___stl_sequent__TOP__0\n"); );
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
    vlSelfRef.Hive_FpAddNarrow__DOT__valid_3 = ((~ (IData)(
                                                           (0U 
                                                            == 
                                                            (0x7fffffffU 
                                                             & vlSelfRef.io_prodFp4_1)))) 
                                                & (IData)(vlSelfRef.io_is4x));
    vlSelfRef.__VdfgRegularize_hebeb780c_0_0 = (1U 
                                                & (~ (IData)(
                                                             (0U 
                                                              == 
                                                              (0x7fffffffU 
                                                               & vlSelfRef.io_prodFp16)))));
    vlSelfRef.__VdfgRegularize_hebeb780c_0_1 = (1U 
                                                & (~ (IData)(
                                                             (0ULL 
                                                              == 
                                                              (0x0000007fffffffffULL 
                                                               & vlSelfRef.io_cReg)))));
    vlSelfRef.Hive_FpAddNarrow__DOT__valid_2 = ((~ (IData)(
                                                           (0U 
                                                            == 
                                                            (0x7fffffffU 
                                                             & vlSelfRef.io_prodFp4_0)))) 
                                                & (IData)(vlSelfRef.io_is4x));
    vlSelfRef.Hive_FpAddNarrow__DOT__valid_1 = ((~ (IData)(
                                                           (0U 
                                                            == 
                                                            (0x7fffffffU 
                                                             & vlSelfRef.io_prodFp8)))) 
                                                & ((IData)(vlSelfRef.io_is4x) 
                                                   | (IData)(vlSelfRef.io_is2x)));
    vlSelfRef.Hive_FpAddNarrow__DOT__e3 = (0x000000ffU 
                                           & ((- (IData)((IData)(vlSelfRef.Hive_FpAddNarrow__DOT__valid_3))) 
                                              & (vlSelfRef.io_prodFp4_1 
                                                 >> 0x00000017U)));
    vlSelfRef.Hive_FpAddNarrow__DOT__e0 = (0x000000ffU 
                                           & ((- (IData)((IData)(vlSelfRef.__VdfgRegularize_hebeb780c_0_0))) 
                                              & (vlSelfRef.io_prodFp16 
                                                 >> 0x00000017U)));
    vlSelfRef.Hive_FpAddNarrow__DOT__eC = (0x000000ffU 
                                           & ((- (IData)((IData)(vlSelfRef.__VdfgRegularize_hebeb780c_0_1))) 
                                              & (IData)(
                                                        (vlSelfRef.io_cReg 
                                                         >> 0x0000001fU))));
    vlSelfRef.Hive_FpAddNarrow__DOT__e2 = (0x000000ffU 
                                           & ((- (IData)((IData)(vlSelfRef.Hive_FpAddNarrow__DOT__valid_2))) 
                                              & (vlSelfRef.io_prodFp4_0 
                                                 >> 0x00000017U)));
    vlSelfRef.Hive_FpAddNarrow__DOT__e1 = (0x000000ffU 
                                           & ((- (IData)((IData)(vlSelfRef.Hive_FpAddNarrow__DOT__valid_1))) 
                                              & (vlSelfRef.io_prodFp8 
                                                 >> 0x00000017U)));
    vlSelfRef.Hive_FpAddNarrow__DOT__eMaxC0 = (((IData)(vlSelfRef.Hive_FpAddNarrow__DOT__eC) 
                                                >= (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__e0))
                                                ? (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__eC)
                                                : (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__e0));
    vlSelfRef.Hive_FpAddNarrow__DOT__eMax12 = (((IData)(vlSelfRef.Hive_FpAddNarrow__DOT__e1) 
                                                >= (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__e2))
                                                ? (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__e1)
                                                : (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__e2));
    vlSelfRef.Hive_FpAddNarrow__DOT__eMaxA = (((IData)(vlSelfRef.Hive_FpAddNarrow__DOT__eMaxC0) 
                                               >= (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__eMax12))
                                               ? (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__eMaxC0)
                                               : (IData)(vlSelfRef.Hive_FpAddNarrow__DOT__eMax12));
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

VL_ATTR_COLD bool VHive_FpAddNarrow___024root___eval_phase__stl(VHive_FpAddNarrow___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VHive_FpAddNarrow___024root___eval_phase__stl\n"); );
    VHive_FpAddNarrow__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    CData/*0:0*/ __VstlExecute;
    // Body
    {
        // Inlined CFunc: _eval_triggers_vec__stl
        vlSelfRef.__VstlTriggered[0U] = ((0xfffffffffffffffeULL 
                                          & vlSelfRef.__VstlTriggered[0U]) 
                                         | (IData)((IData)(vlSelfRef.__VstlFirstIteration)));
    }
#ifdef VL_DEBUG
    if (VL_UNLIKELY(vlSymsp->_vm_contextp__->debug())) {
        VHive_FpAddNarrow___024root___dump_triggers__stl(vlSelfRef.__VstlTriggered, "stl"s);
    }
#endif
    __VstlExecute = VHive_FpAddNarrow___024root___trigger_anySet__stl(vlSelfRef.__VstlTriggered);
    if (__VstlExecute) {
        {
            // Inlined CFunc: _eval_stl
            if ((1ULL & vlSelfRef.__VstlTriggered[0U])) {
                VHive_FpAddNarrow___024root___stl_sequent__TOP__0(vlSelf);
            }
        }
    }
    return (__VstlExecute);
}

bool VHive_FpAddNarrow___024root___trigger_anySet__ico(const VlUnpacked<QData/*63:0*/, 2> &in);

#ifdef VL_DEBUG
VL_ATTR_COLD void VHive_FpAddNarrow___024root___dump_triggers__ico(const VlUnpacked<QData/*63:0*/, 2> &triggers, const std::string &tag) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VHive_FpAddNarrow___024root___dump_triggers__ico\n"); );
    // Body
    if ((1U & (~ (IData)(VHive_FpAddNarrow___024root___trigger_anySet__ico(triggers))))) {
        VL_DBG_MSGS("         No '" + tag + "' region triggers active\n");
    }
    if ((1U & (IData)(triggers[0U]))) {
        VL_DBG_MSGS("         '" + tag + "' region trigger index 0 is active: @( clock)\n");
    }
    if ((1U & (IData)((triggers[0U] >> 1U)))) {
        VL_DBG_MSGS("         '" + tag + "' region trigger index 1 is active: @( reset)\n");
    }
    if ((1U & (IData)((triggers[0U] >> 2U)))) {
        VL_DBG_MSGS("         '" + tag + "' region trigger index 2 is active: @( io_is4x)\n");
    }
    if ((1U & (IData)((triggers[0U] >> 3U)))) {
        VL_DBG_MSGS("         '" + tag + "' region trigger index 3 is active: @( io_is2x)\n");
    }
    if ((1U & (IData)((triggers[0U] >> 4U)))) {
        VL_DBG_MSGS("         '" + tag + "' region trigger index 4 is active: @( io_cReg)\n");
    }
    if ((1U & (IData)((triggers[0U] >> 5U)))) {
        VL_DBG_MSGS("         '" + tag + "' region trigger index 5 is active: @( io_prodFp16)\n");
    }
    if ((1U & (IData)((triggers[0U] >> 6U)))) {
        VL_DBG_MSGS("         '" + tag + "' region trigger index 6 is active: @( io_prodFp8)\n");
    }
    if ((1U & (IData)((triggers[0U] >> 7U)))) {
        VL_DBG_MSGS("         '" + tag + "' region trigger index 7 is active: @( io_prodFp4_0)\n");
    }
    if ((1U & (IData)((triggers[0U] >> 8U)))) {
        VL_DBG_MSGS("         '" + tag + "' region trigger index 8 is active: @( io_prodFp4_1)\n");
    }
    if ((1U & (IData)(triggers[1U]))) {
        VL_DBG_MSGS("         '" + tag + "' region trigger index 64 is active: Internal 'ico' trigger - first iteration\n");
    }
}
#endif  // VL_DEBUG

VL_ATTR_COLD void VHive_FpAddNarrow___024root___ctor_var_reset(VHive_FpAddNarrow___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VHive_FpAddNarrow___024root___ctor_var_reset\n"); );
    VHive_FpAddNarrow__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    const uint64_t __VscopeHash = VL_MURMUR64_HASH(vlSelf->vlNamep);
    vlSelf->clock = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 5452235342940299466ull);
    vlSelf->reset = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 9928399931838511862ull);
    vlSelf->io_is4x = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 9403006408246254646ull);
    vlSelf->io_is2x = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 2269019407539675167ull);
    vlSelf->io_cReg = VL_SCOPED_RAND_RESET_Q(40, __VscopeHash, 11803406362120563831ull);
    vlSelf->io_prodFp16 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 3226741285848674794ull);
    vlSelf->io_prodFp8 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 18140538994153862231ull);
    vlSelf->io_prodFp4_0 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 10824961925864398454ull);
    vlSelf->io_prodFp4_1 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 6284308855990756481ull);
    vlSelf->io_sum = VL_SCOPED_RAND_RESET_Q(40, __VscopeHash, 17102323409486974052ull);
    vlSelf->Hive_FpAddNarrow__DOT__eC = VL_SCOPED_RAND_RESET_I(9, __VscopeHash, 12332968610522956978ull);
    vlSelf->Hive_FpAddNarrow__DOT__e0 = VL_SCOPED_RAND_RESET_I(9, __VscopeHash, 1955291935226974840ull);
    vlSelf->Hive_FpAddNarrow__DOT__valid_1 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 10739017466191329083ull);
    vlSelf->Hive_FpAddNarrow__DOT__e1 = VL_SCOPED_RAND_RESET_I(9, __VscopeHash, 5978497795085997289ull);
    vlSelf->Hive_FpAddNarrow__DOT__valid_2 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 18159624290154092034ull);
    vlSelf->Hive_FpAddNarrow__DOT__e2 = VL_SCOPED_RAND_RESET_I(9, __VscopeHash, 12702057422709464847ull);
    vlSelf->Hive_FpAddNarrow__DOT__valid_3 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 2974383016640142041ull);
    vlSelf->Hive_FpAddNarrow__DOT__e3 = VL_SCOPED_RAND_RESET_I(9, __VscopeHash, 17791952039957095196ull);
    vlSelf->Hive_FpAddNarrow__DOT__eMaxC0 = VL_SCOPED_RAND_RESET_I(9, __VscopeHash, 4645644662357937582ull);
    vlSelf->Hive_FpAddNarrow__DOT__eMax12 = VL_SCOPED_RAND_RESET_I(9, __VscopeHash, 9399014912860520185ull);
    vlSelf->Hive_FpAddNarrow__DOT__eMaxA = VL_SCOPED_RAND_RESET_I(9, __VscopeHash, 5957738561623976451ull);
    vlSelf->__VdfgRegularize_hebeb780c_0_0 = 0;
    vlSelf->__VdfgRegularize_hebeb780c_0_1 = 0;
    for (int __Vi0 = 0; __Vi0 < 1; ++__Vi0) {
        vlSelf->__VstlTriggered[__Vi0] = 0;
    }
    for (int __Vi0 = 0; __Vi0 < 2; ++__Vi0) {
        vlSelf->__VicoTriggered[__Vi0] = 0;
    }
    vlSelf->__Vtrigprevexpr___TOP__clock__0 = 0;
    vlSelf->__Vtrigprevexpr___TOP__reset__0 = 0;
    vlSelf->__Vtrigprevexpr___TOP__io_is4x__0 = 0;
    vlSelf->__Vtrigprevexpr___TOP__io_is2x__0 = 0;
    vlSelf->__Vtrigprevexpr___TOP__io_cReg__0 = 0;
    vlSelf->__Vtrigprevexpr___TOP__io_prodFp16__0 = 0;
    vlSelf->__Vtrigprevexpr___TOP__io_prodFp8__0 = 0;
    vlSelf->__Vtrigprevexpr___TOP__io_prodFp4_0__0 = 0;
    vlSelf->__Vtrigprevexpr___TOP__io_prodFp4_1__0 = 0;
    vlSelf->__VicoDidInit = 0;
}
