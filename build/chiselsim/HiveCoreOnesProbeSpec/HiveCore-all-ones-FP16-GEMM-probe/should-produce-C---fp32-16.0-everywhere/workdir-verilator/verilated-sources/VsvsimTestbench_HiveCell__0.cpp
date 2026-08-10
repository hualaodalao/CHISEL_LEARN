// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See VsvsimTestbench.h for the primary calling header

#include "VsvsimTestbench__pch.h"

void VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0__0(VsvsimTestbench_HiveCell* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.io_loadHIn = ((~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_6)) 
                            & (3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state)));
    vlSelfRef.io_aIn_7 = (((vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_7[3U] 
                            << 0x00000010U) | (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_7[3U] 
                                               >> 0x00000010U)) 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
    vlSelfRef.io_clear = ((IData)(vlSelfRef.io_loadHIn) 
                          & (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_9));
    vlSelfRef.io_loadVLock = ((IData)(vlSelfRef.io_loadHIn) 
                              & ((~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__empty)) 
                                 & (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___io_bPop_ready_T)));
}

void VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0__1(VsvsimTestbench_HiveCell* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.io_aIn_6 = (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_6[3U] 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
    vlSelfRef.io_psumIn_7 = ((- (IData)((IData)(vlSelfRef.io_loadVLock))) 
                             & (((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                                  ? ((- (IData)((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_7))) 
                                     & (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ram_ext__DOT__Memory
                                        [vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value][3U] 
                                        >> 0x00000010U))
                                  : (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_7[7U] 
                                     & vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_1)) 
                                & (- (IData)((1U & 
                                              (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_6)))))));
    vlSelfRef.io_psumIn_6 = ((- (IData)((IData)(vlSelfRef.io_loadVLock))) 
                             & (((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                                  ? (0x0000ffffU & 
                                     ((- (IData)((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_7))) 
                                      & vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ram_ext__DOT__Memory
                                      [vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value][3U]))
                                  : (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_6[6U] 
                                     & vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_1)) 
                                & (- (IData)((1U & 
                                              (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_6)))))));
    vlSelfRef.io_psumIn_5 = ((- (IData)((IData)(vlSelfRef.io_loadVLock))) 
                             & (((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                                  ? ((- (IData)((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_7))) 
                                     & (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ram_ext__DOT__Memory
                                        [vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value][2U] 
                                        >> 0x00000010U))
                                  : (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_5[5U] 
                                     & vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_1)) 
                                & (- (IData)((1U & 
                                              (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_6)))))));
    vlSelfRef.io_psumIn_4 = ((- (IData)((IData)(vlSelfRef.io_loadVLock))) 
                             & (((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                                  ? (0x0000ffffU & 
                                     ((- (IData)((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_7))) 
                                      & vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ram_ext__DOT__Memory
                                      [vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value][2U]))
                                  : (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_4[4U] 
                                     & vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_1)) 
                                & (- (IData)((1U & 
                                              (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_6)))))));
    vlSelfRef.io_psumIn_3 = ((- (IData)((IData)(vlSelfRef.io_loadVLock))) 
                             & (((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                                  ? ((- (IData)((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_7))) 
                                     & (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ram_ext__DOT__Memory
                                        [vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value][1U] 
                                        >> 0x00000010U))
                                  : (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_3[3U] 
                                     & vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_1)) 
                                & (- (IData)((1U & 
                                              (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_6)))))));
    vlSelfRef.io_psumIn_2 = ((- (IData)((IData)(vlSelfRef.io_loadVLock))) 
                             & (((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                                  ? (0x0000ffffU & 
                                     ((- (IData)((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_7))) 
                                      & vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ram_ext__DOT__Memory
                                      [vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value][1U]))
                                  : (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_2[2U] 
                                     & vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_1)) 
                                & (- (IData)((1U & 
                                              (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_6)))))));
    vlSelfRef.io_psumIn_1 = ((- (IData)((IData)(vlSelfRef.io_loadVLock))) 
                             & (((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                                  ? ((- (IData)((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_7))) 
                                     & (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ram_ext__DOT__Memory
                                        [vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value][0U] 
                                        >> 0x00000010U))
                                  : (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_1[1U] 
                                     & vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_1)) 
                                & (- (IData)((1U & 
                                              (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_6)))))));
    vlSelfRef.io_psumIn_0 = ((- (IData)((IData)(vlSelfRef.io_loadVLock))) 
                             & (((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                                  ? (0x0000ffffU & 
                                     ((- (IData)((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_7))) 
                                      & vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ram_ext__DOT__Memory
                                      [vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value][0U]))
                                  : (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_0[0U] 
                                     & vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_1)) 
                                & (- (IData)((1U & 
                                              (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_6)))))));
}

void VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0__2(VsvsimTestbench_HiveCell* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.io_aIn_5 = (((vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_5[2U] 
                            << 0x00000010U) | (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_5[2U] 
                                               >> 0x00000010U)) 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
}

void VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0__3(VsvsimTestbench_HiveCell* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0__3\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.io_aIn_4 = (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_4[2U] 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
}

void VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0__4(VsvsimTestbench_HiveCell* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0__4\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.io_aIn_3 = (((vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_3[1U] 
                            << 0x00000010U) | (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_3[1U] 
                                               >> 0x00000010U)) 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
}

void VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0__5(VsvsimTestbench_HiveCell* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0__5\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.io_aIn_2 = (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_2[1U] 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
}

void VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0__6(VsvsimTestbench_HiveCell* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0__6\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.io_aIn_1 = (((vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_1[0U] 
                            << 0x00000010U) | (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_1[0U] 
                                               >> 0x00000010U)) 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
}

void VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0__7(VsvsimTestbench_HiveCell* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0__7\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.io_aIn_0 = (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_0[0U] 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
    vlSelfRef.io_validIn = (((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__counter) 
                             < (0x0000ffffU & ((IData)(0x001eU) 
                                               + (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__curTileM)))) 
                            & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_10));
    vlSelfRef.io_fmtIn = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_15)
                           ? 2U : (3U & vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__regFile_regs_7));
    vlSelfRef.io_rndIn = (7U & ((vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__regFile_regs_7 
                                 >> 2U) & (- (IData)(
                                                     (1U 
                                                      & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_15)))))));
}

void VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_1__0(VsvsimTestbench_HiveCell* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_1__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.io_psumIn_7 = ((- (IData)((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0.io_loadVLock))) 
                             & (((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                                  ? ((- (IData)((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_7))) 
                                     & (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ram_ext__DOT__Memory
                                        [vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value][7U] 
                                        >> 0x00000010U))
                                  : (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_15[15U] 
                                     & vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_1)) 
                                & (- (IData)((1U & 
                                              (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_6)))))));
    vlSelfRef.io_psumIn_6 = ((- (IData)((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0.io_loadVLock))) 
                             & (((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                                  ? (0x0000ffffU & 
                                     ((- (IData)((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_7))) 
                                      & vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ram_ext__DOT__Memory
                                      [vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value][7U]))
                                  : (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_14[14U] 
                                     & vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_1)) 
                                & (- (IData)((1U & 
                                              (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_6)))))));
    vlSelfRef.io_psumIn_5 = ((- (IData)((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0.io_loadVLock))) 
                             & (((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                                  ? ((- (IData)((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_7))) 
                                     & (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ram_ext__DOT__Memory
                                        [vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value][6U] 
                                        >> 0x00000010U))
                                  : (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_13[13U] 
                                     & vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_1)) 
                                & (- (IData)((1U & 
                                              (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_6)))))));
    vlSelfRef.io_psumIn_4 = ((- (IData)((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0.io_loadVLock))) 
                             & (((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                                  ? (0x0000ffffU & 
                                     ((- (IData)((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_7))) 
                                      & vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ram_ext__DOT__Memory
                                      [vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value][6U]))
                                  : (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_12[12U] 
                                     & vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_1)) 
                                & (- (IData)((1U & 
                                              (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_6)))))));
    vlSelfRef.io_psumIn_3 = ((- (IData)((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0.io_loadVLock))) 
                             & (((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                                  ? ((- (IData)((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_7))) 
                                     & (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ram_ext__DOT__Memory
                                        [vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value][5U] 
                                        >> 0x00000010U))
                                  : (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_11[11U] 
                                     & vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_1)) 
                                & (- (IData)((1U & 
                                              (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_6)))))));
    vlSelfRef.io_psumIn_2 = ((- (IData)((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0.io_loadVLock))) 
                             & (((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                                  ? (0x0000ffffU & 
                                     ((- (IData)((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_7))) 
                                      & vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ram_ext__DOT__Memory
                                      [vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value][5U]))
                                  : (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_10[10U] 
                                     & vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_1)) 
                                & (- (IData)((1U & 
                                              (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_6)))))));
    vlSelfRef.io_psumIn_1 = ((- (IData)((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0.io_loadVLock))) 
                             & (((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                                  ? ((- (IData)((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_7))) 
                                     & (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ram_ext__DOT__Memory
                                        [vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value][4U] 
                                        >> 0x00000010U))
                                  : (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_9[9U] 
                                     & vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_1)) 
                                & (- (IData)((1U & 
                                              (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_6)))))));
    vlSelfRef.io_psumIn_0 = ((- (IData)((IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0.io_loadVLock))) 
                             & (((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                                  ? (0x0000ffffU & 
                                     ((- (IData)((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_7))) 
                                      & vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ram_ext__DOT__Memory
                                      [vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value][4U]))
                                  : (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_8[8U] 
                                     & vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_1)) 
                                & (- (IData)((1U & 
                                              (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_6)))))));
}

void VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_1_0__0(VsvsimTestbench_HiveCell* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_1_0__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.io_aIn_7 = (((vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_15[7U] 
                            << 0x00000010U) | (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_15[7U] 
                                               >> 0x00000010U)) 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
}

void VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_1_0__1(VsvsimTestbench_HiveCell* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_1_0__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.io_aIn_6 = (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_14[7U] 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
}

void VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_1_0__2(VsvsimTestbench_HiveCell* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_1_0__2\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.io_aIn_5 = (((vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_13[6U] 
                            << 0x00000010U) | (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_13[6U] 
                                               >> 0x00000010U)) 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
}

void VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_1_0__3(VsvsimTestbench_HiveCell* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_1_0__3\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.io_aIn_4 = (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_12[6U] 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
}

void VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_1_0__4(VsvsimTestbench_HiveCell* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_1_0__4\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.io_aIn_3 = (((vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_11[5U] 
                            << 0x00000010U) | (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_11[5U] 
                                               >> 0x00000010U)) 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
}

void VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_1_0__5(VsvsimTestbench_HiveCell* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_1_0__5\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.io_aIn_2 = (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_10[5U] 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
}

void VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_1_0__6(VsvsimTestbench_HiveCell* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_1_0__6\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.io_aIn_1 = (((vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_9[4U] 
                            << 0x00000010U) | (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_9[4U] 
                                               >> 0x00000010U)) 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
}

void VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_1_0__7(VsvsimTestbench_HiveCell* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_1_0__7\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.io_aIn_0 = (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_8[4U] 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
}
