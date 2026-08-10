// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See VsvsimTestbench.h for the primary calling header

#include "VsvsimTestbench__pch.h"

VL_ATTR_COLD void VsvsimTestbench_HiveCell___ctor_var_reset(VsvsimTestbench_HiveCell* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+          VsvsimTestbench_HiveCell___ctor_var_reset\n"); );
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
