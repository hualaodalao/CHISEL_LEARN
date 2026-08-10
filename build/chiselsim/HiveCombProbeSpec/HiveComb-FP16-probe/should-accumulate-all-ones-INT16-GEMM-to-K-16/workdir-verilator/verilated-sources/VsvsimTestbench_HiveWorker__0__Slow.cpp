// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See VsvsimTestbench.h for the primary calling header

#include "VsvsimTestbench__pch.h"

VL_ATTR_COLD void VsvsimTestbench_HiveWorker___eval_initial__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_0_0(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___eval_initial__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_0_0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    VlUnpacked<IData/*31:0*/, 3> __PVT__unnamedblk3__DOT___RANDOM;
    for (int __Vi0 = 0; __Vi0 < 3; ++__Vi0) {
        __PVT__unnamedblk3__DOT___RANDOM[__Vi0] = 0;
    }
    // Body
    for (int __Vi0 = 0; __Vi0 < 3; ++__Vi0) {
        __PVT__unnamedblk3__DOT___RANDOM[__Vi0] = 0;
    }
    __PVT__unnamedblk3__DOT___RANDOM[0U] = VL_RANDOM_I();
    __PVT__unnamedblk3__DOT___RANDOM[1U] = VL_RANDOM_I();
    __PVT__unnamedblk3__DOT___RANDOM[2U] = VL_RANDOM_I();
    vlSelfRef.__PVT__fmtReg = (3U & (__PVT__unnamedblk3__DOT___RANDOM[0U] 
                                     >> 2U));
    vlSelfRef.__PVT__rndReg = (7U & (__PVT__unnamedblk3__DOT___RANDOM[0U] 
                                     >> 4U));
    vlSelfRef.__PVT__wReg = (0x0000ffffU & (__PVT__unnamedblk3__DOT___RANDOM[0U] 
                                            >> 7U));
    vlSelfRef.__PVT__psumReg = ((0xff800000U & __PVT__unnamedblk3__DOT___RANDOM[0U]) 
                                | (0x007fffffU & __PVT__unnamedblk3__DOT___RANDOM[1U]));
    vlSelfRef.__PVT__io_aOut_REG = ((0x0000ff80U & 
                                     (__PVT__unnamedblk3__DOT___RANDOM[1U] 
                                      >> 0x00000010U)) 
                                    | (0x0000007fU 
                                       & __PVT__unnamedblk3__DOT___RANDOM[2U]));
    vlSelfRef.__PVT__io_validOut_REG = (1U & (__PVT__unnamedblk3__DOT___RANDOM[2U] 
                                              >> 7U));
    vlSelfRef.__PVT__io_fmtOut_REG = (3U & (__PVT__unnamedblk3__DOT___RANDOM[2U] 
                                            >> 8U));
    vlSelfRef.__PVT__io_rndOut_REG = (7U & (__PVT__unnamedblk3__DOT___RANDOM[2U] 
                                            >> 0x0aU));
}

VL_ATTR_COLD void VsvsimTestbench_HiveWorker___eval_initial__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_0_7(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___eval_initial__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_0_7\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    VlUnpacked<IData/*31:0*/, 3> __PVT__unnamedblk3__DOT___RANDOM;
    for (int __Vi0 = 0; __Vi0 < 3; ++__Vi0) {
        __PVT__unnamedblk3__DOT___RANDOM[__Vi0] = 0;
    }
    // Body
    for (int __Vi0 = 0; __Vi0 < 3; ++__Vi0) {
        __PVT__unnamedblk3__DOT___RANDOM[__Vi0] = 0;
    }
    __PVT__unnamedblk3__DOT___RANDOM[0U] = VL_RANDOM_I();
    __PVT__unnamedblk3__DOT___RANDOM[1U] = VL_RANDOM_I();
    __PVT__unnamedblk3__DOT___RANDOM[2U] = VL_RANDOM_I();
    vlSelfRef.__PVT__fmtReg = (3U & (__PVT__unnamedblk3__DOT___RANDOM[0U] 
                                     >> 2U));
    vlSelfRef.__PVT__rndReg = (7U & (__PVT__unnamedblk3__DOT___RANDOM[0U] 
                                     >> 4U));
    vlSelfRef.__PVT__wReg = (0x0000ffffU & (__PVT__unnamedblk3__DOT___RANDOM[0U] 
                                            >> 7U));
    vlSelfRef.__PVT__psumReg = ((0xff800000U & __PVT__unnamedblk3__DOT___RANDOM[0U]) 
                                | (0x007fffffU & __PVT__unnamedblk3__DOT___RANDOM[1U]));
    vlSelfRef.__PVT__io_aOut_REG = ((0x0000ff80U & 
                                     (__PVT__unnamedblk3__DOT___RANDOM[1U] 
                                      >> 0x00000010U)) 
                                    | (0x0000007fU 
                                       & __PVT__unnamedblk3__DOT___RANDOM[2U]));
    vlSelfRef.__PVT__io_validOut_REG = (1U & (__PVT__unnamedblk3__DOT___RANDOM[2U] 
                                              >> 7U));
}

VL_ATTR_COLD void VsvsimTestbench_HiveWorker___eval_initial__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_1_7(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___eval_initial__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_1_7\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    VlUnpacked<IData/*31:0*/, 3> __PVT__unnamedblk3__DOT___RANDOM;
    for (int __Vi0 = 0; __Vi0 < 3; ++__Vi0) {
        __PVT__unnamedblk3__DOT___RANDOM[__Vi0] = 0;
    }
    // Body
    for (int __Vi0 = 0; __Vi0 < 3; ++__Vi0) {
        __PVT__unnamedblk3__DOT___RANDOM[__Vi0] = 0;
    }
    __PVT__unnamedblk3__DOT___RANDOM[0U] = VL_RANDOM_I();
    __PVT__unnamedblk3__DOT___RANDOM[1U] = VL_RANDOM_I();
    __PVT__unnamedblk3__DOT___RANDOM[2U] = VL_RANDOM_I();
    vlSelfRef.__PVT__fmtReg = (3U & (__PVT__unnamedblk3__DOT___RANDOM[0U] 
                                     >> 2U));
    vlSelfRef.__PVT__rndReg = (7U & (__PVT__unnamedblk3__DOT___RANDOM[0U] 
                                     >> 4U));
    vlSelfRef.__PVT__wReg = (0x0000ffffU & (__PVT__unnamedblk3__DOT___RANDOM[0U] 
                                            >> 7U));
    vlSelfRef.__PVT__psumReg = ((0xff800000U & __PVT__unnamedblk3__DOT___RANDOM[0U]) 
                                | (0x007fffffU & __PVT__unnamedblk3__DOT___RANDOM[1U]));
    vlSelfRef.__PVT__io_aOut_REG = ((0x0000ff80U & 
                                     (__PVT__unnamedblk3__DOT___RANDOM[1U] 
                                      >> 0x00000010U)) 
                                    | (0x0000007fU 
                                       & __PVT__unnamedblk3__DOT___RANDOM[2U]));
}

VL_ATTR_COLD void VsvsimTestbench_HiveWorker___eval_initial__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_7(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___eval_initial__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__pes_7_7\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    VlUnpacked<IData/*31:0*/, 3> __PVT__unnamedblk3__DOT___RANDOM;
    for (int __Vi0 = 0; __Vi0 < 3; ++__Vi0) {
        __PVT__unnamedblk3__DOT___RANDOM[__Vi0] = 0;
    }
    // Body
    for (int __Vi0 = 0; __Vi0 < 3; ++__Vi0) {
        __PVT__unnamedblk3__DOT___RANDOM[__Vi0] = 0;
    }
    __PVT__unnamedblk3__DOT___RANDOM[0U] = VL_RANDOM_I();
    __PVT__unnamedblk3__DOT___RANDOM[1U] = VL_RANDOM_I();
    __PVT__unnamedblk3__DOT___RANDOM[2U] = VL_RANDOM_I();
    vlSelfRef.__PVT__fmtReg = (3U & (__PVT__unnamedblk3__DOT___RANDOM[0U] 
                                     >> 2U));
    vlSelfRef.__PVT__rndReg = (7U & (__PVT__unnamedblk3__DOT___RANDOM[0U] 
                                     >> 4U));
    vlSelfRef.__PVT__wReg = (0x0000ffffU & (__PVT__unnamedblk3__DOT___RANDOM[0U] 
                                            >> 7U));
    vlSelfRef.__PVT__psumReg = ((0xff800000U & __PVT__unnamedblk3__DOT___RANDOM[0U]) 
                                | (0x007fffffU & __PVT__unnamedblk3__DOT___RANDOM[1U]));
    vlSelfRef.__PVT__io_aOut_REG = ((0x0000ff80U & 
                                     (__PVT__unnamedblk3__DOT___RANDOM[1U] 
                                      >> 0x00000010U)) 
                                    | (0x0000007fU 
                                       & __PVT__unnamedblk3__DOT___RANDOM[2U]));
    vlSelfRef.__PVT__io_fmtOut_REG = (3U & (__PVT__unnamedblk3__DOT___RANDOM[2U] 
                                            >> 8U));
    vlSelfRef.__PVT__io_rndOut_REG = (7U & (__PVT__unnamedblk3__DOT___RANDOM[2U] 
                                            >> 0x0aU));
}

VL_ATTR_COLD void VsvsimTestbench_HiveWorker___ctor_var_reset(VsvsimTestbench_HiveWorker* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+            VsvsimTestbench_HiveWorker___ctor_var_reset\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    const uint64_t __VscopeHash = VL_MURMUR64_HASH(vlSelf->vlNamep);
    vlSelf->__PVT__clock = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 5452235342940299466ull);
    vlSelf->__PVT__reset = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 9928399931838511862ull);
    vlSelf->__PVT__io_aIn = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 9436503435736357765ull);
    vlSelf->__PVT__io_aOut = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 2915779943878250693ull);
    vlSelf->__PVT__io_psumIn = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 12372357165803283730ull);
    vlSelf->__PVT__io_psumOut = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 2417062124076075382ull);
    vlSelf->__PVT__io_loadHIn = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 7420298287209565457ull);
    vlSelf->__PVT__io_loadVLock = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 1848609066412887328ull);
    vlSelf->__PVT__io_loadVIn = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 4967396123023891649ull);
    vlSelf->__PVT__io_validIn = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 3142964429747571378ull);
    vlSelf->__PVT__io_validOut = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 2134219546585533004ull);
    vlSelf->__PVT__io_fmtIn = VL_SCOPED_RAND_RESET_I(2, __VscopeHash, 9643427063419145653ull);
    vlSelf->__PVT__io_fmtOut = VL_SCOPED_RAND_RESET_I(2, __VscopeHash, 14445940669893818696ull);
    vlSelf->__PVT__io_rndIn = VL_SCOPED_RAND_RESET_I(3, __VscopeHash, 2741106929692592414ull);
    vlSelf->__PVT__io_rndOut = VL_SCOPED_RAND_RESET_I(3, __VscopeHash, 13667529630304862117ull);
    vlSelf->__PVT__io_clear = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 3036369959689982536ull);
    vlSelf->__PVT__fmtReg = VL_SCOPED_RAND_RESET_I(2, __VscopeHash, 8790608001394970881ull);
    vlSelf->__PVT__rndReg = VL_SCOPED_RAND_RESET_I(3, __VscopeHash, 4326911166844221594ull);
    vlSelf->__PVT__wReg = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 3457238435170325336ull);
    vlSelf->__PVT__psumReg = VL_SCOPED_RAND_RESET_I(32, __VscopeHash, 1128887095844064373ull);
    vlSelf->__PVT__io_aOut_REG = VL_SCOPED_RAND_RESET_I(16, __VscopeHash, 15008868841126028511ull);
    vlSelf->__PVT__io_validOut_REG = VL_SCOPED_RAND_RESET_I(1, __VscopeHash, 9836702963489711904ull);
    vlSelf->__PVT__io_fmtOut_REG = VL_SCOPED_RAND_RESET_I(2, __VscopeHash, 10384933223285533849ull);
    vlSelf->__PVT__io_rndOut_REG = VL_SCOPED_RAND_RESET_I(3, __VscopeHash, 5046144481111385156ull);
}
