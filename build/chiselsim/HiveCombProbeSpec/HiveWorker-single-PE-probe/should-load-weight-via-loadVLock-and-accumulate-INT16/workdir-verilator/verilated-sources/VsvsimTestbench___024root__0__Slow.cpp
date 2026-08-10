// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See VsvsimTestbench.h for the primary calling header

#include "VsvsimTestbench__pch.h"

VL_ATTR_COLD void VsvsimTestbench___024root___eval_static(VsvsimTestbench___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___eval_static\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    {
        // Inlined CFunc: _eval_static__TOP
        vlSelfRef.svsimTestbench__DOT__clock = 0U;
        vlSelfRef.svsimTestbench__DOT__reset = 0U;
        vlSelfRef.svsimTestbench__DOT__io_clear = 0U;
        vlSelfRef.svsimTestbench__DOT__io_rndIn = 0U;
        vlSelfRef.svsimTestbench__DOT__io_fmtIn = 0U;
        vlSelfRef.svsimTestbench__DOT__io_validIn = 0U;
        vlSelfRef.svsimTestbench__DOT__io_loadVIn = 0U;
        vlSelfRef.svsimTestbench__DOT__io_loadVLock = 0U;
        vlSelfRef.svsimTestbench__DOT__io_loadHIn = 0U;
        vlSelfRef.svsimTestbench__DOT__io_psumIn = 0U;
        vlSelfRef.svsimTestbench__DOT__io_aIn = 0U;
        vlSelfRef.svsimTestbench__DOT__simulationState = 0U;
    }
    vlSelfRef.__Vtrigprevexpr___TOP__svsimTestbench__DOT__simulationState__0 = 0U;
    vlSelfRef.__Vtrigprevexpr___TOP__svsimTestbench__DOT__clock__0 
        = vlSelfRef.svsimTestbench__DOT__clock;
}

VL_ATTR_COLD void VsvsimTestbench___024root___eval_initial__TOP(VsvsimTestbench___024root* vlSelf);

VL_ATTR_COLD void VsvsimTestbench___024root___eval_initial(VsvsimTestbench___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___eval_initial\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    VsvsimTestbench___024root___eval_initial__TOP(vlSelf);
}

void VsvsimTestbench___024root____Vdpiimwrap_svsimTestbench__DOT__initTestBenchScope_TOP(const VerilatedScope* __Vscopep, const char* __Vfilenamep, IData/*31:0*/ __Vlineno);

VL_ATTR_COLD void VsvsimTestbench___024root___eval_initial__TOP(VsvsimTestbench___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___eval_initial__TOP\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    VlUnpacked<IData/*31:0*/, 3> svsimTestbench__DOT__dut__DOT__unnamedblk3__DOT___RANDOM;
    for (int __Vi0 = 0; __Vi0 < 3; ++__Vi0) {
        svsimTestbench__DOT__dut__DOT__unnamedblk3__DOT___RANDOM[__Vi0] = 0;
    }
    // Body
    VsvsimTestbench___024root____Vdpiimwrap_svsimTestbench__DOT__initTestBenchScope_TOP(
                                                                                (vlSymsp->__Vscopep_svsimTestbench), 
                                                                                "/Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv", 0x00000028U);
    vlSelfRef.svsimTestbench__DOT__simulationState = 1U;
    for (int __Vi0 = 0; __Vi0 < 3; ++__Vi0) {
        svsimTestbench__DOT__dut__DOT__unnamedblk3__DOT___RANDOM[__Vi0] = 0;
    }
    svsimTestbench__DOT__dut__DOT__unnamedblk3__DOT___RANDOM[0U] 
        = VL_RANDOM_I();
    svsimTestbench__DOT__dut__DOT__unnamedblk3__DOT___RANDOM[1U] 
        = VL_RANDOM_I();
    svsimTestbench__DOT__dut__DOT__unnamedblk3__DOT___RANDOM[2U] 
        = VL_RANDOM_I();
    vlSelfRef.svsimTestbench__DOT__dut__DOT__fmtReg 
        = (3U & (svsimTestbench__DOT__dut__DOT__unnamedblk3__DOT___RANDOM[0U] 
                 >> 2U));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__rndReg 
        = (7U & (svsimTestbench__DOT__dut__DOT__unnamedblk3__DOT___RANDOM[0U] 
                 >> 4U));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__wReg = 
        (0x0000ffffU & (svsimTestbench__DOT__dut__DOT__unnamedblk3__DOT___RANDOM[0U] 
                        >> 7U));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__psumReg 
        = ((0xff800000U & svsimTestbench__DOT__dut__DOT__unnamedblk3__DOT___RANDOM[0U]) 
           | (0x007fffffU & svsimTestbench__DOT__dut__DOT__unnamedblk3__DOT___RANDOM[1U]));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__io_aOut_REG 
        = ((0x0000ff80U & (svsimTestbench__DOT__dut__DOT__unnamedblk3__DOT___RANDOM[1U] 
                           >> 0x00000010U)) | (0x0000007fU 
                                               & svsimTestbench__DOT__dut__DOT__unnamedblk3__DOT___RANDOM[2U]));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__io_validOut_REG 
        = (1U & (svsimTestbench__DOT__dut__DOT__unnamedblk3__DOT___RANDOM[2U] 
                 >> 7U));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__io_fmtOut_REG 
        = (3U & (svsimTestbench__DOT__dut__DOT__unnamedblk3__DOT___RANDOM[2U] 
                 >> 8U));
    vlSelfRef.svsimTestbench__DOT__dut__DOT__io_rndOut_REG 
        = (7U & (svsimTestbench__DOT__dut__DOT__unnamedblk3__DOT___RANDOM[2U] 
                 >> 0x0aU));
}

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
                                                                                "/Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv", 0x0000012dU);
}

VL_ATTR_COLD void VsvsimTestbench___024root___eval_settle(VsvsimTestbench___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___eval_settle\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
}

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
    vlSelf->svsimTestbench__DOT__dut__DOT__fmtReg = VL_SCOPED_RAND_RESET_I(2, __VscopeHash, 1390978497030933068ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__rndReg = VL_SCOPED_RAND_RESET_I(3, __VscopeHash, 14455871662313260354ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__wReg = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 13585120746829605656ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__psumReg = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 6677436971500113461ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__io_aOut_REG = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 10883596071533299459ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__io_validOut_REG = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 13155283660997971259ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__io_fmtOut_REG = VL_SCOPED_RAND_RESET_I(2, __VscopeHash, 18085156667665636441ull);
    vlSelf->svsimTestbench__DOT__dut__DOT__io_rndOut_REG = VL_SCOPED_RAND_RESET_I(3, __VscopeHash, 13551780505882601087ull);
    vlSelf->__Vdpi_export_trigger = 0;
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
