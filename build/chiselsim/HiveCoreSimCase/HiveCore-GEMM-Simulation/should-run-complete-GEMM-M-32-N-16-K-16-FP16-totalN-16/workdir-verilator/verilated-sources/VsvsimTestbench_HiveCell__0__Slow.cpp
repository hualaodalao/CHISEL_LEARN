// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See VsvsimTestbench.h for the primary calling header

#include "VsvsimTestbench__pch.h"

VL_ATTR_COLD void VsvsimTestbench_HiveCell___stl_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0__0(VsvsimTestbench_HiveCell* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveCell___stl_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.io_rndIn = (7U & (((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                                  ? (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__regFile_regs_7 
                                     >> 2U) : ((vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__regFile_regs_7 
                                                >> 2U) 
                                               & (- (IData)(
                                                            (1U 
                                                             & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_9))))))) 
                                & (- (IData)((1U & 
                                              (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_4)))))));
    vlSelfRef.io_fmtIn = ((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_4)
                           ? 2U : (3U & ((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                                          ? vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__regFile_regs_7
                                          : ((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_9)
                                              ? 2U : vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__regFile_regs_7))));
    vlSelfRef.io_loadHIn = ((~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_4)) 
                            & (3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state)));
    vlSelfRef.io_validIn = (((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__counter) 
                             < (0x0000ffffU & ((IData)(0x001eU) 
                                               + (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__curTileM)))) 
                            & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_8));
    vlSelfRef.io_aIn_7 = (((vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_7[3U] 
                            << 0x00000010U) | (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_7[3U] 
                                               >> 0x00000010U)) 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
    vlSelfRef.io_aIn_6 = (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_6[3U] 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
    vlSelfRef.io_aIn_5 = (((vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_5[2U] 
                            << 0x00000010U) | (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_5[2U] 
                                               >> 0x00000010U)) 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
    vlSelfRef.io_aIn_4 = (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_4[2U] 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
    vlSelfRef.io_aIn_3 = (((vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_3[1U] 
                            << 0x00000010U) | (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_3[1U] 
                                               >> 0x00000010U)) 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
    vlSelfRef.io_aIn_2 = (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_2[1U] 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
    vlSelfRef.io_aIn_1 = (((vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_1[0U] 
                            << 0x00000010U) | (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_1[0U] 
                                               >> 0x00000010U)) 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
    vlSelfRef.io_aIn_0 = (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_0[0U] 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
    vlSelfRef.io_clear = ((IData)(vlSelfRef.io_loadHIn) 
                          & (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_7));
    vlSelfRef.io_loadVLock = ((IData)(vlSelfRef.io_loadHIn) 
                              & ((~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__empty)) 
                                 & (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___io_bPop_ready_T)));
}

VL_ATTR_COLD void VsvsimTestbench_HiveCell___stl_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0__1(VsvsimTestbench_HiveCell* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveCell___stl_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0__1\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.io_psumIn_7 = (((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                               ? ((- (IData)((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_5))) 
                                  & (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ram_ext__DOT__Memory
                                     [vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value][3U] 
                                     >> 0x00000010U))
                               : (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_7[7U] 
                                  & vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_1)) 
                             & (- (IData)((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_4))))));
    vlSelfRef.io_psumIn_6 = (((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                               ? (0x0000ffffU & ((- (IData)((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_5))) 
                                                 & vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ram_ext__DOT__Memory
                                                 [vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value][3U]))
                               : (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_6[6U] 
                                  & vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_1)) 
                             & (- (IData)((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_4))))));
    vlSelfRef.io_psumIn_5 = (((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                               ? ((- (IData)((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_5))) 
                                  & (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ram_ext__DOT__Memory
                                     [vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value][2U] 
                                     >> 0x00000010U))
                               : (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_5[5U] 
                                  & vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_1)) 
                             & (- (IData)((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_4))))));
    vlSelfRef.io_psumIn_4 = (((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                               ? (0x0000ffffU & ((- (IData)((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_5))) 
                                                 & vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ram_ext__DOT__Memory
                                                 [vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value][2U]))
                               : (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_4[4U] 
                                  & vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_1)) 
                             & (- (IData)((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_4))))));
    vlSelfRef.io_psumIn_3 = (((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                               ? ((- (IData)((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_5))) 
                                  & (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ram_ext__DOT__Memory
                                     [vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value][1U] 
                                     >> 0x00000010U))
                               : (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_3[3U] 
                                  & vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_1)) 
                             & (- (IData)((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_4))))));
    vlSelfRef.io_psumIn_2 = (((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                               ? (0x0000ffffU & ((- (IData)((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_5))) 
                                                 & vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ram_ext__DOT__Memory
                                                 [vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value][1U]))
                               : (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_2[2U] 
                                  & vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_1)) 
                             & (- (IData)((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_4))))));
    vlSelfRef.io_psumIn_1 = (((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                               ? ((- (IData)((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_5))) 
                                  & (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ram_ext__DOT__Memory
                                     [vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value][0U] 
                                     >> 0x00000010U))
                               : (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_1[1U] 
                                  & vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_1)) 
                             & (- (IData)((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_4))))));
    vlSelfRef.io_psumIn_0 = (((3U == (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
                               ? (0x0000ffffU & ((- (IData)((IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_5))) 
                                                 & vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ram_ext__DOT__Memory
                                                 [vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value][0U]))
                               : (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_0[0U] 
                                  & vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_1)) 
                             & (- (IData)((1U & (~ (IData)(vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_4))))));
}

VL_ATTR_COLD void VsvsimTestbench_HiveCell___stl_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_1_0__0(VsvsimTestbench_HiveCell* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveCell___stl_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_1_0__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.io_aIn_7 = (((vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_15[7U] 
                            << 0x00000010U) | (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_15[7U] 
                                               >> 0x00000010U)) 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
    vlSelfRef.io_aIn_6 = (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_14[7U] 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
    vlSelfRef.io_aIn_5 = (((vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_13[6U] 
                            << 0x00000010U) | (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_13[6U] 
                                               >> 0x00000010U)) 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
    vlSelfRef.io_aIn_4 = (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_12[6U] 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
    vlSelfRef.io_aIn_3 = (((vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_11[5U] 
                            << 0x00000010U) | (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_11[5U] 
                                               >> 0x00000010U)) 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
    vlSelfRef.io_aIn_2 = (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_10[5U] 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
    vlSelfRef.io_aIn_1 = (((vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_9[4U] 
                            << 0x00000010U) | (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_9[4U] 
                                               >> 0x00000010U)) 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
    vlSelfRef.io_aIn_0 = (vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_8[4U] 
                          & (IData)(vlSymsp->TOP.__VdfgRegularize_hebeb780c_0_3));
}

VL_ATTR_COLD void VsvsimTestbench_HiveCell___ctor_var_reset(VsvsimTestbench_HiveCell* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveCell___ctor_var_reset\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    const uint64_t __VscopeHash = VL_MURMUR64_HASH(vlSelf->vlNamep);
    vlSelf->clock = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 5452235342940299466ull);
    vlSelf->reset = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 9928399931838511862ull);
    vlSelf->io_aIn_0 = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 17849381708546618066ull);
    vlSelf->io_aIn_1 = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 13454346162792497704ull);
    vlSelf->io_aIn_2 = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 3069446527410724087ull);
    vlSelf->io_aIn_3 = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 16994373941974942647ull);
    vlSelf->io_aIn_4 = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 15893820308661544389ull);
    vlSelf->io_aIn_5 = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 335450763334962145ull);
    vlSelf->io_aIn_6 = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 7089777209580916659ull);
    vlSelf->io_aIn_7 = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 6124396269920957140ull);
    vlSelf->io_aOut_0 = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 14336867944390183639ull);
    vlSelf->io_aOut_1 = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 8233480068251386432ull);
    vlSelf->io_aOut_2 = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 9459344744207841829ull);
    vlSelf->io_aOut_3 = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 13538161305689862032ull);
    vlSelf->io_aOut_4 = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 15775284122426207845ull);
    vlSelf->io_aOut_5 = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 7067761091937484879ull);
    vlSelf->io_aOut_6 = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 10501286753377688291ull);
    vlSelf->io_aOut_7 = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 7682284842011028968ull);
    vlSelf->io_psumIn_0 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 14172124126564125703ull);
    vlSelf->io_psumIn_1 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 3816355696038065226ull);
    vlSelf->io_psumIn_2 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 1206106403975122043ull);
    vlSelf->io_psumIn_3 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 3208468244833455261ull);
    vlSelf->io_psumIn_4 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 17780209039385379333ull);
    vlSelf->io_psumIn_5 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 4593672955044847815ull);
    vlSelf->io_psumIn_6 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 8732250424089502442ull);
    vlSelf->io_psumIn_7 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 17011232409217276073ull);
    vlSelf->io_cOut_0 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 7657875743959315017ull);
    vlSelf->io_cOut_1 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 11019476415680472925ull);
    vlSelf->io_cOut_2 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 17583307613645354768ull);
    vlSelf->io_cOut_3 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 3779646388821833404ull);
    vlSelf->io_cOut_4 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 4637149026543524114ull);
    vlSelf->io_cOut_5 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 17130062348800536294ull);
    vlSelf->io_cOut_6 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 8446084350836522416ull);
    vlSelf->io_cOut_7 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 6641362459244416947ull);
    vlSelf->io_clear = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 3036369959689982536ull);
    vlSelf->io_loadVLock = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 1848609066412887328ull);
    vlSelf->io_validIn = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 3142964429747571378ull);
    vlSelf->io_fmtIn = VL_SCOPED_RAND_RESET_I(2, __VscopeHash, 9643427063419145653ull);
    vlSelf->io_rndIn = VL_SCOPED_RAND_RESET_I(3, __VscopeHash, 2741106929692592414ull);
    vlSelf->io_loadHIn = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 7420298287209565457ull);
    vlSelf->io_loadVIn = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 4967396123023891649ull);
    vlSelf->io_validOut_0 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 12470487769280333999ull);
    vlSelf->io_validOut_1 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 6125347556660299365ull);
    vlSelf->io_validOut_2 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 8013387979798460317ull);
    vlSelf->io_validOut_3 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 12150202188425660927ull);
    vlSelf->io_validOut_4 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 15474326088189080757ull);
    vlSelf->io_validOut_5 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 15140194050825502479ull);
    vlSelf->io_validOut_6 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 10262161765287577575ull);
    vlSelf->io_validOut_7 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 16619995337660127666ull);
    vlSelf->io_fmtOut = VL_SCOPED_RAND_RESET_I(2, __VscopeHash, 14445940669893818696ull);
    vlSelf->io_rndOut = VL_SCOPED_RAND_RESET_I(3, __VscopeHash, 13667529630304862117ull);
}
