// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See VsvsimTestbench.h for the primary calling header

#include "VsvsimTestbench__pch.h"

VL_ATTR_COLD void VsvsimTestbench___024root___eval_final__TOP(VsvsimTestbench___024root* vlSelf);

VL_ATTR_COLD void VsvsimTestbench___024root___eval_final(VsvsimTestbench___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___eval_final\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    VsvsimTestbench___024root___eval_final__TOP(vlSelf);
}

void VsvsimTestbench___024root____Vdpiimwrap_svsimTestbench__DOT__simulation_final_TOP(const VerilatedScope* __Vscopep, const char* __Vfilenamep, IData/*31:0*/ __Vlineno);

VL_ATTR_COLD void VsvsimTestbench___024root___eval_final__TOP(VsvsimTestbench___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___eval_final__TOP\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    VsvsimTestbench___024root____Vdpiimwrap_svsimTestbench__DOT__simulation_final_TOP(
                                                                                (vlSymsp->__Vscopep_svsimTestbench), 
                                                                                "/Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreWeightMapProbeSpec/HiveCore-weight-mapping-probe/should-place-PE-x-y-.wReg---B-x--y---ascending-k-/workdir-verilator/../generated-sources/testbench.sv", 0x00000303U);
}

#ifdef VL_DEBUG
VL_ATTR_COLD void VsvsimTestbench___024root___dump_triggers__stl(const VlUnpacked<QData/*63:0*/, 1> &triggers, const std::string &tag);
#endif  // VL_DEBUG
VL_ATTR_COLD bool VsvsimTestbench___024root___eval_phase__stl(VsvsimTestbench___024root* vlSelf);

VL_ATTR_COLD void VsvsimTestbench___024root___eval_settle(VsvsimTestbench___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___eval_settle\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    IData/*31:0*/ __VstlIterCount;
    // Body
    __VstlIterCount = 0U;
    vlSelfRef.__VstlFirstIteration = 1U;
    do {
        if (VL_UNLIKELY(((0x00002710U < __VstlIterCount)))) {
#ifdef VL_DEBUG
            VsvsimTestbench___024root___dump_triggers__stl(vlSelfRef.__VstlTriggered, "stl"s);
#endif
            VL_FATAL_MT("/Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreWeightMapProbeSpec/HiveCore-weight-mapping-probe/should-place-PE-x-y-.wReg---B-x--y---ascending-k-/workdir-verilator/../generated-sources/testbench.sv", 1, "", "DIDNOTCONVERGE: Settle region did not converge after '--converge-limit' of 10000 tries");
        }
        __VstlIterCount = ((IData)(1U) + __VstlIterCount);
        vlSelfRef.__VstlPhaseResult = VsvsimTestbench___024root___eval_phase__stl(vlSelf);
        vlSelfRef.__VstlFirstIteration = 0U;
    } while (vlSelfRef.__VstlPhaseResult);
}

VL_ATTR_COLD bool VsvsimTestbench___024root___trigger_anySet__stl(const VlUnpacked<QData/*63:0*/, 1> &in);

#ifdef VL_DEBUG
VL_ATTR_COLD void VsvsimTestbench___024root___dump_triggers__stl(const VlUnpacked<QData/*63:0*/, 1> &triggers, const std::string &tag) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___dump_triggers__stl\n"); );
    // Body
    if ((1U & (~ (IData)(VsvsimTestbench___024root___trigger_anySet__stl(triggers))))) {
        VL_DBG_MSGS("         No '" + tag + "' region triggers active\n");
    }
    if ((1U & (IData)(triggers[0U]))) {
        VL_DBG_MSGS("         '" + tag + "' region trigger index 0 is active: Internal 'stl' trigger - first iteration\n");
    }
}
#endif  // VL_DEBUG

VL_ATTR_COLD bool VsvsimTestbench___024root___trigger_anySet__stl(const VlUnpacked<QData/*63:0*/, 1> &in) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___trigger_anySet__stl\n"); );
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

VL_ATTR_COLD void VsvsimTestbench___024root___stl_sequent__TOP__0(VsvsimTestbench___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___stl_sequent__TOP__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__ptr_match;
    svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__ptr_match = 0;
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ptr_match;
    svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ptr_match = 0;
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__full;
    svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__full = 0;
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__scratchpad__DOT__aFifo__DOT__queue__DOT__ptr_match;
    svsimTestbench__DOT__dut__DOT__scratchpad__DOT__aFifo__DOT__queue__DOT__ptr_match = 0;
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__scratchpad__DOT__aFifo__DOT__queue__DOT__full;
    svsimTestbench__DOT__dut__DOT__scratchpad__DOT__aFifo__DOT__queue__DOT__full = 0;
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__executor__DOT___io_cPop_ready_T;
    svsimTestbench__DOT__dut__DOT__executor__DOT___io_cPop_ready_T = 0;
    SData/*10:0*/ __VdfgRegularize_hebeb780c_0_5;
    __VdfgRegularize_hebeb780c_0_5 = 0;
    CData/*0:0*/ __VdfgRegularize_hebeb780c_0_11;
    __VdfgRegularize_hebeb780c_0_11 = 0;
    CData/*0:0*/ __VdfgRegularize_hebeb780c_0_12;
    __VdfgRegularize_hebeb780c_0_12 = 0;
    CData/*0:0*/ __VdfgRegularize_hebeb780c_0_13;
    __VdfgRegularize_hebeb780c_0_13 = 0;
    // Body
    vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___nextNTile_T 
        = (0x0000ffffU & ((IData)(1U) + (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__curNTile)));
    vlSelfRef.svsimTestbench__DOT__dut__DOT___executor_io_flushC 
        = ((1U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__state)) 
           & ((0U != (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__state)) 
              & (0U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__curKTile))));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__cDma__DOT___GEN_3 
        = ((0U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__cDma__DOT__state)) 
           | (1U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__cDma__DOT__state)));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___layerCapture 
        = ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__alignedValid) 
           & (5U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__state)));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___layerCapture_0 
        = ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__alignCnt) 
           < (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__curTileM));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_6 
        = ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__loadedRows) 
           >= (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__curTileK));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__bDma__DOT___GEN_2 
        = ((0U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__bDma__DOT__state)) 
           | (1U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__bDma__DOT__state)));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__aDma__DOT___GEN_2 
        = ((0U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__aDma__DOT__state)) 
           | (1U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__aDma__DOT__state)));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_9 
        = ((5U != (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__state)) 
           | (4U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__state)));
    svsimTestbench__DOT__dut__DOT__executor__DOT___io_cPop_ready_T 
        = ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__counter) 
           < (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__curTileM));
    vlSelfRef.svsimTestbench__DOT__dut__DOT___executePulse_T 
        = ((~ (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__respValid)) 
           & (IData)(vlSelfRef.svsimTestbench__DOT__io_cmd_valid));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_21 
        = ((0x0000ffffU & ((IData)(1U) + (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__curNTile))) 
           >= (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__nTiles));
    svsimTestbench__DOT__dut__DOT__scratchpad__DOT__aFifo__DOT__queue__DOT__ptr_match 
        = ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__aFifo__DOT__queue__DOT__deq_ptr_value) 
           == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__aFifo__DOT__queue__DOT__enq_ptr_value));
    __VdfgRegularize_hebeb780c_0_5 = (0x000007ffU & 
                                      ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__enq_ptr_value) 
                                       - (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__deq_ptr_value)));
    __VdfgRegularize_hebeb780c_0_11 = ((5U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__state)) 
                                       | ((6U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__state)) 
                                          | (7U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__state))));
    svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__ptr_match 
        = ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__deq_ptr_value) 
           == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__enq_ptr_value));
    __VdfgRegularize_hebeb780c_0_12 = ((3U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__state)) 
                                       | (4U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__state)));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___io_bPop_ready_T 
        = ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__loadedRows) 
           < (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__curTileK));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_12 
        = ((5U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__state)) 
           & (0U != (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__curKTile)));
    svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ptr_match 
        = ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value) 
           == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__enq_ptr_value));
    __VdfgRegularize_hebeb780c_0_13 = ((1U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__state)) 
                                       | (2U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__state)));
    vlSelfRef.__VdfgRegularize_hebeb780c_0_10 = ((~ (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__cDma__DOT___GEN_3)) 
                                                 & (2U 
                                                    == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__cDma__DOT__state)));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__io_cPush_valid_0 
        = ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___layerCapture_0) 
           & (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___layerCapture));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_7 
        = ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_6) 
           & (0U != (0x00000fffU & ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__counter) 
                                    >> 4U))));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__executePulse 
        = ((2U == (IData)(vlSelfRef.svsimTestbench__DOT__io_cmd_payload_op)) 
           & (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT___executePulse_T));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_22 
        = (1U & (~ ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__dmaStarted) 
                    | (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_21))));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__aFifo__DOT__queue__DOT__empty 
        = ((~ (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__aFifo__DOT__queue__DOT__maybe_full)) 
           & (IData)(svsimTestbench__DOT__dut__DOT__scratchpad__DOT__aFifo__DOT__queue__DOT__ptr_match));
    svsimTestbench__DOT__dut__DOT__scratchpad__DOT__aFifo__DOT__queue__DOT__full 
        = ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__aFifo__DOT__queue__DOT__maybe_full) 
           & (IData)(svsimTestbench__DOT__dut__DOT__scratchpad__DOT__aFifo__DOT__queue__DOT__ptr_match));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__empty 
        = ((~ (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__maybe_full)) 
           & (IData)(svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__ptr_match));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__full 
        = ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__maybe_full) 
           & (IData)(svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__ptr_match));
    vlSelfRef.__VdfgRegularize_hebeb780c_0_1 = (- (IData)(
                                                          (1U 
                                                           & (~ 
                                                              ((~ (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_12)) 
                                                               | (4U 
                                                                  == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__state)))))));
    svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__full 
        = ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__maybe_full) 
           & (IData)(svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ptr_match));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__empty 
        = ((~ (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__maybe_full)) 
           & (IData)(svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ptr_match));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_17 
        = ((IData)(__VdfgRegularize_hebeb780c_0_13) 
           | ((IData)(__VdfgRegularize_hebeb780c_0_12) 
              | (IData)(__VdfgRegularize_hebeb780c_0_11)));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_4 
        = ((0U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__state)) 
           | (IData)(__VdfgRegularize_hebeb780c_0_13));
    vlSelfRef.svsimTestbench__DOT__dut__DOT___cDma_io_bufPop_ready 
        = ((IData)(vlSelfRef.svsimTestbench__DOT__io_dma1Ext_writeData_ready) 
           & (IData)(vlSelfRef.__VdfgRegularize_hebeb780c_0_10));
    vlSelfRef.svsimTestbench__DOT__dut__DOT___executor_io_flushB 
        = ((0U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__state)) 
           & (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executePulse));
    vlSelfRef.svsimTestbench__DOT__dut__DOT___scratchpad_io_aOccupancy 
        = (((IData)(svsimTestbench__DOT__dut__DOT__scratchpad__DOT__aFifo__DOT__queue__DOT__full) 
            << 0x0000000bU) | (0x000007ffU & ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__aFifo__DOT__queue__DOT__enq_ptr_value) 
                                              - (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__aFifo__DOT__queue__DOT__deq_ptr_value))));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__aDma__DOT__io_dmaExtRdIF_rsp_ready_0 
        = ((~ (IData)(svsimTestbench__DOT__dut__DOT__scratchpad__DOT__aFifo__DOT__queue__DOT__full)) 
           & ((~ (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__aDma__DOT___GEN_2)) 
              & (2U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__aDma__DOT__state))));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__do_enq 
        = ((~ (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__full)) 
           & (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__io_cPush_valid_0));
    vlSelfRef.svsimTestbench__DOT__dut__DOT___scratchpad_io_cOccupancy 
        = (((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__full) 
            << 0x0000000bU) | (IData)(__VdfgRegularize_hebeb780c_0_5));
    vlSelfRef.svsimTestbench__DOT__dut__DOT___scratchpad_io_bOccupancy 
        = (((IData)(svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__full) 
            << 6U) | (0x0000003fU & ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__enq_ptr_value) 
                                     - (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value))));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__bDma__DOT__io_dmaExtRdIF_rsp_ready_0 
        = ((~ (IData)(svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__full)) 
           & ((~ (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__bDma__DOT___GEN_2)) 
              & (2U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__bDma__DOT__state))));
    vlSelfRef.__VdfgRegularize_hebeb780c_0_9 = (1U 
                                                & (~ 
                                                   ((0U 
                                                     == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__state)) 
                                                    | (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_17))));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_10 
        = ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_4) 
           | (IData)(__VdfgRegularize_hebeb780c_0_12));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__aDma__DOT__io_dmaExtRdIF_cmd_valid_0 
        = ((1U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__aDma__DOT__state)) 
           & ((0U != (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__aDma__DOT__state)) 
              & (0U != (0x00000fffU & ((IData)(0x0800U) 
                                       - (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT___scratchpad_io_aOccupancy))))));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__aDma__DOT___GEN_3 
        = ((IData)(vlSelfRef.svsimTestbench__DOT__io_dma0Ext_rsp_valid) 
           & (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__aDma__DOT__io_dmaExtRdIF_rsp_ready_0));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_20 
        = ((8U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__state)) 
           & (0U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT___scratchpad_io_cOccupancy)));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__bDma__DOT__io_dmaExtRdIF_cmd_valid_0 
        = ((1U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__bDma__DOT__state)) 
           & ((0U != (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__bDma__DOT__state)) 
              & (0U != (0x0000007fU & ((IData)(0x40U) 
                                       - (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT___scratchpad_io_bOccupancy))))));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__do_enq 
        = ((IData)(vlSelfRef.svsimTestbench__DOT__io_dma2Ext_rsp_valid) 
           & (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__bDma__DOT__io_dmaExtRdIF_rsp_ready_0));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__cDma__DOT___GEN_1 
        = ((IData)(vlSelfRef.__VdfgRegularize_hebeb780c_0_9) 
           & ((8U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__state)) 
              & ((0U != (IData)(__VdfgRegularize_hebeb780c_0_5)) 
                 | (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__full))));
    vlSelfRef.svsimTestbench__DOT__dut__DOT___executor_io_done 
        = ((~ ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_10) 
               | ((IData)(__VdfgRegularize_hebeb780c_0_11) 
                  | ((8U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__state)) 
                     | (9U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__state)))))) 
           & (0x0aU == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__state)));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__io_cPop_ready_0 
        = ((~ (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_10)) 
           & ((IData)(svsimTestbench__DOT__dut__DOT__executor__DOT___io_cPop_ready_T) 
              & (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_12)));
    vlSelfRef.__VdfgRegularize_hebeb780c_0_8 = ((~ (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_10)) 
                                                & (5U 
                                                   == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__state)));
    vlSelfRef.__VdfgRegularize_hebeb780c_0_3 = (0x0000ffffU 
                                                & (- (IData)(
                                                             (1U 
                                                              & (~ 
                                                                 ((5U 
                                                                   != (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__state)) 
                                                                  | (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_10)))))));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_23 
        = ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_22) 
           & (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_20));
    vlSelfRef.svsimTestbench__DOT__dut__DOT____Vcellinp__cDma__io_bufPop_valid 
        = (1U & (~ ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__io_cPop_ready_0) 
                    | (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__empty))));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__io_aPop_ready_0 
        = ((IData)(svsimTestbench__DOT__dut__DOT__executor__DOT___io_cPop_ready_T) 
           & (IData)(vlSelfRef.__VdfgRegularize_hebeb780c_0_8));
    vlSelfRef.svsimTestbench__DOT__dut__DOT____Vcellinp__aDma__io_start 
        = (((IData)(vlSelfRef.__VdfgRegularize_hebeb780c_0_9) 
            & (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_23)) 
           | (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executePulse));
    vlSelfRef.svsimTestbench__DOT__dut__DOT___executor_io_flushA 
        = ((0U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__state))
            ? (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executePulse)
            : ((~ (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_17)) 
               & (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_23)));
}

VL_ATTR_COLD void VsvsimTestbench_HiveCell___stl_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0__0(VsvsimTestbench_HiveCell* vlSelf);
VL_ATTR_COLD void VsvsimTestbench_HiveCell___stl_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_1_0__0(VsvsimTestbench_HiveCell* vlSelf);
VL_ATTR_COLD void VsvsimTestbench_HiveCell___stl_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0__1(VsvsimTestbench_HiveCell* vlSelf);
void VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_1__0(VsvsimTestbench_HiveCell* vlSelf);

VL_ATTR_COLD bool VsvsimTestbench___024root___eval_phase__stl(VsvsimTestbench___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___eval_phase__stl\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
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
        VsvsimTestbench___024root___dump_triggers__stl(vlSelfRef.__VstlTriggered, "stl"s);
    }
#endif
    __VstlExecute = VsvsimTestbench___024root___trigger_anySet__stl(vlSelfRef.__VstlTriggered);
    if (__VstlExecute) {
        {
            // Inlined CFunc: _eval_stl
            if ((1ULL & vlSelfRef.__VstlTriggered[0U])) {
                VsvsimTestbench___024root___stl_sequent__TOP__0(vlSelf);
                VsvsimTestbench_HiveCell___stl_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0));
                VsvsimTestbench_HiveCell___stl_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_1_0__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_1_0));
                {
                    // Inlined CFunc: _nba_sequent__TOP__2
                    vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__io_bPop_ready_0 
                        = ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___io_bPop_ready_T) 
                           & (IData)(vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0.io_loadHIn));
                    vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_5 
                        = ((~ (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__empty)) 
                           & (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__executor__DOT__io_bPop_ready_0));
                }
                VsvsimTestbench_HiveCell___stl_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0));
                VsvsimTestbench_HiveCell___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_1__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_1));
            }
        }
    }
    return (__VstlExecute);
}

bool VsvsimTestbench___024root___trigger_anySet__ico(const VlUnpacked<QData/*63:0*/, 1> &in);

#ifdef VL_DEBUG
VL_ATTR_COLD void VsvsimTestbench___024root___dump_triggers__ico(const VlUnpacked<QData/*63:0*/, 1> &triggers, const std::string &tag) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___dump_triggers__ico\n"); );
    // Body
    if ((1U & (~ (IData)(VsvsimTestbench___024root___trigger_anySet__ico(triggers))))) {
        VL_DBG_MSGS("         No '" + tag + "' region triggers active\n");
    }
    if ((1U & (IData)(triggers[0U]))) {
        VL_DBG_MSGS("         '" + tag + "' region trigger index 0 is active: Internal 'ico' trigger - first iteration\n");
    }
    if ((1U & (IData)((triggers[0U] >> 1U)))) {
        VL_DBG_MSGS("         '" + tag + "' region trigger index 1 is active: Internal 'ico' trigger - DPI export trigger\n");
    }
}
#endif  // VL_DEBUG

bool VsvsimTestbench___024root___trigger_anySet__act(const VlUnpacked<QData/*63:0*/, 2> &in);

#ifdef VL_DEBUG
VL_ATTR_COLD void VsvsimTestbench___024root___dump_triggers__act(const VlUnpacked<QData/*63:0*/, 2> &triggers, const std::string &tag) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___dump_triggers__act\n"); );
    // Body
    if ((1U & (~ (IData)(VsvsimTestbench___024root___trigger_anySet__act(triggers))))) {
        VL_DBG_MSGS("         No '" + tag + "' region triggers active\n");
    }
    if ((1U & (IData)(triggers[0U]))) {
        VL_DBG_MSGS("         '" + tag + "' region trigger index 0 is active: @( svsimTestbench.simulationState)\n");
    }
    if ((1U & (IData)((triggers[0U] >> 1U)))) {
        VL_DBG_MSGS("         '" + tag + "' region trigger index 1 is active: @(posedge svsimTestbench.clock)\n");
    }
    if ((1U & (IData)(triggers[1U]))) {
        VL_DBG_MSGS("         '" + tag + "' region trigger index 64 is active: Internal 'act' trigger - DPI export trigger\n");
    }
}
#endif  // VL_DEBUG

VL_ATTR_COLD void VsvsimTestbench___024root___ctor_var_reset(VsvsimTestbench___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___ctor_var_reset\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    const uint64_t __VscopeHash = VL_MURMUR64_HASH(vlSelf->vlNamep);
    vlSelf->svsimTestbench__DOT__dut__DOT__respValid = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 8381803471448556967ull);
    vlSelf->svsimTestbench__DOT__dut__DOT___executor_io_done = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 11940658508391733706ull);
    vlSelf->svsimTestbench__DOT__dut__DOT___executor_io_flushA = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 2675623876025727215ull);
    vlSelf->svsimTestbench__DOT__dut__DOT___executor_io_flushB = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 8271799634345323725ull);
    vlSelf->svsimTestbench__DOT__dut__DOT___executor_io_flushC = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 6512474667784598459ull);
    vlSelf->svsimTestbench__DOT__dut__DOT___cDma_io_bufPop_ready = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 13008037930038518879ull);
    vlSelf->svsimTestbench__DOT__dut__DOT___scratchpad_io_aOccupancy = VL_SCOPED_RAND_RESET_I(12, __VscopeHash, 17386508613832172666ull);
    vlSelf->svsimTestbench__DOT__dut__DOT___scratchpad_io_bOccupancy = VL_SCOPED_RAND_RESET_I(7, __VscopeHash, 9595481135395725223ull);
    vlSelf->svsimTestbench__DOT__dut__DOT___scratchpad_io_cOccupancy = VL_SCOPED_RAND_RESET_I(12, __VscopeHash, 1993142683816465487ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__regFile_regs_0 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 7484818435228687784ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__regFile_regs_1 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 14605525496783576623ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__regFile_regs_2 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 3964077005594185732ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__regFile_regs_3 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 13996938866881275080ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__regFile_regs_4 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 18287700170388488449ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__regFile_regs_5 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 13067946541465590685ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__regFile_regs_6 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 16503154894112213018ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__regFile_regs_7 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 18189171248418844988ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__regFile_regs_8 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 4267701010898725379ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__doneFlag = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 7576986192696234023ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__errFlag = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 5428843670098133831ull);
    vlSelf->svsimTestbench__DOT__dut__DOT___executePulse_T = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 17737617917641135217ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executePulse = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 12205658080352416138ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__respData = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 802866819828233123ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__respDone = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 4562364962917458759ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__respErr = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 6717679909452426081ull);
    vlSelf->svsimTestbench__DOT__dut__DOT____Vcellinp__aDma__io_start = 0;
    vlSelf->svsimTestbench__DOT__dut__DOT____Vcellinp__cDma__io_bufPop_valid = 0;
    vlSelf->svsimTestbench__DOT__dut__DOT__hiveComb__DOT__validSkewedPerRow_r = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 16356137412536486696ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__hiveComb__DOT__validSkewedPerRow_r_1 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 7545833370507093862ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__hiveComb__DOT__validSkewedPerRow_r_2 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 9984793817565455572ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__hiveComb__DOT__validSkewedPerRow_r_3 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 677739985547077837ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__hiveComb__DOT__validSkewedPerRow_r_4 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 4589617158534561492ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__hiveComb__DOT__validSkewedPerRow_r_5 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 3572582842779732269ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__hiveComb__DOT__validSkewedPerRow_r_6 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 11271692083861723817ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__hiveComb__DOT__validSkewedPerRow_1 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 13360616532914898735ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__enq_ptr_value = VL_SCOPED_RAND_RESET_I(11, __VscopeHash, 1797160404429163380ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__deq_ptr_value = VL_SCOPED_RAND_RESET_I(11, __VscopeHash, 6739098657990652903ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__maybe_full = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 1381348203019542406ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__empty = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 2403557797350373366ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__full = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 8687823656416019470ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__do_enq = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 8712785605780805320ull);
    for (int __Vi0 = 0; __Vi0 < 2048; ++__Vi0) {
        VL_SCOPED_RAND_RESET_W(512, vlSelf->svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__ram_ext__DOT__Memory[__Vi0], __VscopeHash, 9434426703413891654ull);
    }
    VL_SCOPED_RAND_RESET_W(512, vlSelf->svsimTestbench__DOT__dut__DOT__scratchpad__DOT__cFifo__DOT__queue__DOT__ram_ext__DOT___RANDOM_MEM, __VscopeHash, 18105468492332437261ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__enq_ptr_value = VL_SCOPED_RAND_RESET_I(6, __VscopeHash, 17596734926705615828ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__deq_ptr_value = VL_SCOPED_RAND_RESET_I(6, __VscopeHash, 1283097449884181298ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__maybe_full = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 9049088591979670577ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__empty = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 4502695118306767557ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__do_enq = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 11799960052422644477ull);
    for (int __Vi0 = 0; __Vi0 < 64; ++__Vi0) {
        VL_SCOPED_RAND_RESET_W(256, vlSelf->svsimTestbench__DOT__dut__DOT__scratchpad__DOT__bFifo__DOT__queue__DOT__ram_ext__DOT__Memory[__Vi0], __VscopeHash, 8847201573686426050ull);
    }
    vlSelf->svsimTestbench__DOT__dut__DOT__scratchpad__DOT__aFifo__DOT__queue__DOT__enq_ptr_value = VL_SCOPED_RAND_RESET_I(11, __VscopeHash, 1991191183522856753ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__scratchpad__DOT__aFifo__DOT__queue__DOT__deq_ptr_value = VL_SCOPED_RAND_RESET_I(11, __VscopeHash, 16542607170077833458ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__scratchpad__DOT__aFifo__DOT__queue__DOT__maybe_full = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 5645877264472078704ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__scratchpad__DOT__aFifo__DOT__queue__DOT__empty = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 5196130238885460265ull);
    for (int __Vi0 = 0; __Vi0 < 2048; ++__Vi0) {
        VL_SCOPED_RAND_RESET_W(256, vlSelf->svsimTestbench__DOT__dut__DOT__scratchpad__DOT__aFifo__DOT__queue__DOT__ram_ext__DOT__Memory[__Vi0], __VscopeHash, 4107237304038982839ull);
    }
    VL_SCOPED_RAND_RESET_W(256, vlSelf->svsimTestbench__DOT__dut__DOT__scratchpad__DOT__aFifo__DOT__queue__DOT__ram_ext__DOT___RANDOM_MEM, __VscopeHash, 3193565723313248801ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__state = VL_SCOPED_RAND_RESET_I(4, __VscopeHash, 12750200655010971238ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__errState = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 11798242625428473201ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__mTiles = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 2442747721558316870ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__nTiles = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 15246231026126652361ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__kTiles = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 8759862819468504662ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__curMTile = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 7118016470363960687ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__curNTile = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 5401370495463375773ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__curKTile = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 12911098308497331097ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__doneTiles = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 7107629651126541687ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__curTileM = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 2169174032998701290ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__curTileK = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 5651073513886582743ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__depthOfCBufferUsed = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 11228304750720915594ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__counter = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 16960416860343799904ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__totalCycles = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 5002923955309441242ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__loadedRows = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 697971376451675355ull);
    VL_SCOPED_RAND_RESET_W(256, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_0, __VscopeHash, 16251931508250532790ull);
    VL_SCOPED_RAND_RESET_W(256, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_1, __VscopeHash, 3514372040615848100ull);
    VL_SCOPED_RAND_RESET_W(256, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_2, __VscopeHash, 12987571054885377950ull);
    VL_SCOPED_RAND_RESET_W(256, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_3, __VscopeHash, 13330376143593066895ull);
    VL_SCOPED_RAND_RESET_W(256, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_4, __VscopeHash, 5923975576763247168ull);
    VL_SCOPED_RAND_RESET_W(256, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_5, __VscopeHash, 13138094482712808809ull);
    VL_SCOPED_RAND_RESET_W(256, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_6, __VscopeHash, 7777451679058793861ull);
    VL_SCOPED_RAND_RESET_W(256, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_7, __VscopeHash, 15295031197679925061ull);
    VL_SCOPED_RAND_RESET_W(256, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_8, __VscopeHash, 10052126982396022556ull);
    VL_SCOPED_RAND_RESET_W(256, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_9, __VscopeHash, 15027592104348115760ull);
    VL_SCOPED_RAND_RESET_W(256, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_10, __VscopeHash, 10845057279210869632ull);
    VL_SCOPED_RAND_RESET_W(256, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_11, __VscopeHash, 3756317531842560877ull);
    VL_SCOPED_RAND_RESET_W(256, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_12, __VscopeHash, 18033049512341053379ull);
    VL_SCOPED_RAND_RESET_W(256, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_13, __VscopeHash, 17760493830333119148ull);
    VL_SCOPED_RAND_RESET_W(256, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_14, __VscopeHash, 5959541240984974564ull);
    VL_SCOPED_RAND_RESET_W(256, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__aRegs_15, __VscopeHash, 1789529147774001166ull);
    VL_SCOPED_RAND_RESET_W(512, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_0, __VscopeHash, 13917891114501822269ull);
    VL_SCOPED_RAND_RESET_W(512, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_1, __VscopeHash, 7225116052697048588ull);
    VL_SCOPED_RAND_RESET_W(512, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_2, __VscopeHash, 12658100853768689628ull);
    VL_SCOPED_RAND_RESET_W(512, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_3, __VscopeHash, 14472471269587848972ull);
    VL_SCOPED_RAND_RESET_W(512, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_4, __VscopeHash, 4372244884143028763ull);
    VL_SCOPED_RAND_RESET_W(512, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_5, __VscopeHash, 4032741139553407803ull);
    VL_SCOPED_RAND_RESET_W(512, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_6, __VscopeHash, 560127064579328457ull);
    VL_SCOPED_RAND_RESET_W(512, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_7, __VscopeHash, 9835310203648303754ull);
    VL_SCOPED_RAND_RESET_W(512, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_8, __VscopeHash, 11913034445411138652ull);
    VL_SCOPED_RAND_RESET_W(512, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_9, __VscopeHash, 5220259383606132971ull);
    VL_SCOPED_RAND_RESET_W(512, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_10, __VscopeHash, 267765208563651663ull);
    VL_SCOPED_RAND_RESET_W(512, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_11, __VscopeHash, 16578182017046087781ull);
    VL_SCOPED_RAND_RESET_W(512, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_12, __VscopeHash, 5936617613946439929ull);
    VL_SCOPED_RAND_RESET_W(512, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_13, __VscopeHash, 4355085827168615875ull);
    VL_SCOPED_RAND_RESET_W(512, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_14, __VscopeHash, 11875633460566217019ull);
    VL_SCOPED_RAND_RESET_W(512, vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cpsRegs_15, __VscopeHash, 16665749764462395163ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 7844855458016461611ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_1 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 8016473117569585557ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_2 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 1900791694128798778ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_3 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 16614092403760581439ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_4 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 708722820949524012ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_5 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 5636823514117580132ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_6 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 5795676954933004168ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_7 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 17434791494699278417ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_8 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 16571731556733635244ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_9 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 3324743315834581272ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_10 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 14989762373995385025ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_11 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 3141994979741437435ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_12 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 7701880306112246966ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_13 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 832958817023097818ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_0 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 3983475823795226146ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_14 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 12493738100393261927ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_15 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 9086466072063050356ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_16 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 11020694254525058682ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_17 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 10344496850436731702ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_18 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 4765165353610170484ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_19 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 2241028154481417704ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_20 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 5228661702808925324ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_21 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 14463755949905575901ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_22 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 4370737854235791762ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_23 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 9497151119790988436ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_24 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 5937014904016935715ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_25 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 3076409903493432825ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_26 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 11010655585188855122ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_1 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 13141906988472992578ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_27 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 16999600983855527089ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_28 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 6990882513741970207ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_29 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 16182084946550724193ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_30 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 15033578972101489377ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_31 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 7192573038690378935ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_32 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 13709491108825149337ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_33 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 14355169168060840304ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_34 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 10450366378940049719ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_35 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 12941757742089901066ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_36 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 6292931058694314986ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_37 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 384679704566437645ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_38 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 1692953518427032479ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_2 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 171726920432958930ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_39 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 10011410655963117496ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_40 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 12335526963153565748ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_41 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 17406209903227673038ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_42 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 9128972817539967425ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_43 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 11894740334599797310ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_44 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 18401177345457512789ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_45 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 1043763741350201244ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_46 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 16101329569468276601ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_47 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 14296988063255611080ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_48 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 1392618387771504207ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_49 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 5456548616619557392ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_3 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 2831923836574037118ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_50 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 14213962586129378546ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_51 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 17585520502642342243ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_52 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 16189845557012466551ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_53 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 16208529378610590929ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_54 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 12361259204264672495ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_55 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 17337232349308667292ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_56 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 16684747696755986613ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_57 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 7128890659283707611ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_58 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 15354454816192822271ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_59 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 7572289263708312821ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_4 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 17255574761719617498ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_60 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 1001037143662041182ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_61 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 13318516332576239252ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_62 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 506898426606403625ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_63 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 18078660179176706383ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_64 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 16098872559874602004ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_65 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 395066816008680523ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_66 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 13427712343819540733ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_67 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 100843841894342999ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_68 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 2074914422679579154ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_5 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 74812170526514731ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_69 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 16305676320677165330ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_70 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 16534776135032856036ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_71 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 966886146871820057ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_72 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 6998804634852299541ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_73 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 7431818777978032ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_74 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 922093689152476193ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_75 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 16513710520022685539ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_76 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 12484038447419507264ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_6 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 6526111313476494252ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_77 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 10751577549020579823ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_78 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 176931713322126430ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_79 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 6127922417872032809ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_80 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 17447222931618128784ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_81 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 7399580788155091341ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_82 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 5719563788024607952ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_83 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 252703192482964809ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_7 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 8013247537772970549ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_84 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 12079790456471798880ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_85 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 11185371425787081631ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_86 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 11898795261958767385ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_87 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 2835076642505664582ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_88 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 271479975796134044ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_89 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 844499913356446119ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_8 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 4104583884504284686ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_90 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 6012724378615886437ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_91 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 3948849234673705122ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_92 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 7092290513803340401ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_93 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 3085447628416353294ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_94 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 2311162476736800574ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_9 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 13136428891319385387ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_95 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 14752400758787957946ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_96 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 11499138268901433851ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_97 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 4654671884554909875ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_98 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 17909547183836543302ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_10 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 8468790087085154634ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_99 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 14489976713331599224ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_100 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 12942063632815722915ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_101 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 15911656691322575313ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_11 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 1749601567668354619ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_102 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 10789721111213210618ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_103 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 4334541967757421430ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_12 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 8859995409226930613ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_r_104 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 3008532319330368052ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_13 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 13901708730300640059ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__cDeSkewed_14 = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 4532627489894952840ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__alignedValid_r = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 4300224679888203206ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__alignedValid_r_1 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 12301224396434086492ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__alignedValid_r_2 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 14480031842748826610ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__alignedValid_r_3 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 6449174469577252609ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__alignedValid_r_4 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 6425971947093746209ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__alignedValid_r_5 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 11487635792832349439ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__alignedValid_r_6 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 15448121395170147919ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__alignedValid_r_7 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 14631583334406246265ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__alignedValid_r_8 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 7394061499515264457ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__alignedValid_r_9 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 320304843372393963ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__alignedValid_r_10 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 9746058478880343777ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__alignedValid_r_11 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 8383185933350825922ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__alignedValid_r_12 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 10498761754955588411ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__alignedValid_r_13 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 7336576633150718197ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__alignedValid_r_14 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 4483908184435465905ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__alignedValid = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 7358806402678718307ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__alignCnt = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 2543006132000874858ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__drainCounter = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 1015908321980780456ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__dmaStarted = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 10977814077613150816ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT___layerCapture = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 14484214670184265964ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT___layerCapture_0 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 7269466857360382391ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__io_cPush_valid_0 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 2227188574889416206ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_4 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 8884505387598128282ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT___io_bPop_ready_T = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 18340873143608254463ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__io_bPop_ready_0 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 14394751301798304635ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_5 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 10733918785817083580ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_6 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 2907126612639992072ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_7 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 16328259638281567796ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_9 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 1716321459716456581ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_10 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 13512419485254195638ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__io_aPop_ready_0 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 6384509865974639717ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_12 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 10818050338547988780ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT__io_cPop_ready_0 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 2144670971638212141ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_17 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 17903133445908681635ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_20 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 3816497770025882485ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT___nextNTile_T = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 13840491083849234422ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_21 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 9403478310686201076ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_22 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 2919957061675691093ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__executor__DOT___GEN_23 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 12639931909012781032ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__bDma__DOT__state = VL_SCOPED_RAND_RESET_I(2, __VscopeHash, 766146295517368000ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__bDma__DOT__curAddr = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 7004190271858567036ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__bDma__DOT__blkTarget = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 17187055815817992483ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__bDma__DOT__rowCnt = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 371268198591377789ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__bDma__DOT__blkCnt = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 14702699086852608731ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__bDma__DOT__errReg = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 5303965157840664187ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__bDma__DOT__isAReg = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 18124204997493728358ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__bDma__DOT__ktCnt = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 5404364959451146016ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__bDma__DOT__ktRows = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 9389666569201977377ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__bDma__DOT__rowsLastReg = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 17521603704483833504ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__bDma__DOT__bRowStep = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 646442347243544191ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__bDma__DOT__bKtJump = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 8169045173244501503ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__bDma__DOT__bKtJumpLast = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 13970484588895085335ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__bDma__DOT__bNJump = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 4012172461779223221ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__bDma__DOT__io_dmaExtRdIF_cmd_valid_0 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 5129628755556270750ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__bDma__DOT___GEN_2 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 17499498726068912238ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__bDma__DOT__io_dmaExtRdIF_rsp_ready_0 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 4435385571391807988ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__cDma__DOT__state = VL_SCOPED_RAND_RESET_I(2, __VscopeHash, 12862054217143605960ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__cDma__DOT__curAddr = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 8603409345286629459ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__cDma__DOT__rowStep = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 11364970104218435741ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__cDma__DOT__blkJump = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 16366515416860664416ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__cDma__DOT__innerRows = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 4546506749288461335ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__cDma__DOT__blkTarget = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 5446627445201280642ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__cDma__DOT__rowCnt = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 9955854780390218232ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__cDma__DOT__blkCnt = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 5977542998525391466ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__cDma__DOT___GEN_1 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 9491476046499847813ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__cDma__DOT___GEN_3 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 2698123058477329076ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__aDma__DOT__state = VL_SCOPED_RAND_RESET_I(2, __VscopeHash, 11779274645979709451ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__aDma__DOT__curAddr = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 10332924616241799845ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__aDma__DOT__rowStep = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 9416168293688138456ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__aDma__DOT__blkJump = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 10727150190558035770ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__aDma__DOT__innerRows = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 4575658822545487594ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__aDma__DOT__blkTarget = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 4637434308755521904ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__aDma__DOT__rowCnt = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 4103220946831905581ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__aDma__DOT__blkCnt = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 14624823482230185678ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__aDma__DOT__errReg = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 3812367193497000351ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__aDma__DOT__io_dmaExtRdIF_cmd_valid_0 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 13291376364232916593ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__aDma__DOT___GEN_2 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 17529287875450064051ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__aDma__DOT__io_dmaExtRdIF_rsp_ready_0 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 1605075434538011347ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__aDma__DOT___GEN_3 = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 9989828114433024880ull);
    vlSelf->__Vdpi_export_trigger = 0;
    vlSelf->__VdfgRegularize_hebeb780c_0_1 = 0;
    vlSelf->__VdfgRegularize_hebeb780c_0_3 = 0;
    vlSelf->__VdfgRegularize_hebeb780c_0_8 = 0;
    vlSelf->__VdfgRegularize_hebeb780c_0_9 = 0;
    vlSelf->__VdfgRegularize_hebeb780c_0_10 = 0;
    for (int __Vi0 = 0; __Vi0 < 1; ++__Vi0) {
        vlSelf->__VstlTriggered[__Vi0] = 0;
    }
    for (int __Vi0 = 0; __Vi0 < 1; ++__Vi0) {
        vlSelf->__VicoTriggered[__Vi0] = 0;
    }
    for (int __Vi0 = 0; __Vi0 < 2; ++__Vi0) {
        vlSelf->__VactTriggered[__Vi0] = 0;
    }
    vlSelf->__Vtrigprevexpr___TOP__svsimTestbench__DOT__simulationState__0 = 0;
    vlSelf->__Vtrigprevexpr___TOP__svsimTestbench__DOT__clock__0 = 0;
    vlSelf->__VactDidInit = 0;
    for (int __Vi0 = 0; __Vi0 < 2; ++__Vi0) {
        vlSelf->__VnbaTriggered[__Vi0] = 0;
    }
}
