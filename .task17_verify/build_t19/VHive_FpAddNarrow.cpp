// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Model implementation (design independent parts)

#include "VHive_FpAddNarrow__pch.h"

//============================================================
// Constructors

VHive_FpAddNarrow::VHive_FpAddNarrow(VerilatedContext* _vcontextp__, const char* _vcname__)
    : VerilatedModel{*_vcontextp__}
    , vlSymsp{new VHive_FpAddNarrow__Syms(contextp(), _vcname__, this)}
    , io_is4x{vlSymsp->TOP.io_is4x}
    , io_is2x{vlSymsp->TOP.io_is2x}
    , io_prodFp16{vlSymsp->TOP.io_prodFp16}
    , io_prodFp8{vlSymsp->TOP.io_prodFp8}
    , io_prodFp4_0{vlSymsp->TOP.io_prodFp4_0}
    , io_prodFp4_1{vlSymsp->TOP.io_prodFp4_1}
    , io_cReg{vlSymsp->TOP.io_cReg}
    , io_sum{vlSymsp->TOP.io_sum}
    , rootp{&(vlSymsp->TOP)}
{
    // Register model with the context
    contextp()->addModel(this);
}

VHive_FpAddNarrow::VHive_FpAddNarrow(const char* _vcname__)
    : VHive_FpAddNarrow(Verilated::threadContextp(), _vcname__)
{
}

//============================================================
// Destructor

VHive_FpAddNarrow::~VHive_FpAddNarrow() {
    delete vlSymsp;
}

//============================================================
// Evaluation function

#ifdef VL_DEBUG
void VHive_FpAddNarrow___024root___eval_debug_assertions(VHive_FpAddNarrow___024root* vlSelf);
#endif  // VL_DEBUG
void VHive_FpAddNarrow___024root___eval_static(VHive_FpAddNarrow___024root* vlSelf);
void VHive_FpAddNarrow___024root___eval_initial(VHive_FpAddNarrow___024root* vlSelf);
void VHive_FpAddNarrow___024root___eval_settle(VHive_FpAddNarrow___024root* vlSelf);
void VHive_FpAddNarrow___024root___eval(VHive_FpAddNarrow___024root* vlSelf);

void VHive_FpAddNarrow::eval_step() {
    VL_DEBUG_IF(VL_DBG_MSGF("+++++TOP Evaluate VHive_FpAddNarrow::eval_step\n"); );
#ifdef VL_DEBUG
    // Debug assertions
    VHive_FpAddNarrow___024root___eval_debug_assertions(&(vlSymsp->TOP));
#endif  // VL_DEBUG
    vlSymsp->__Vm_deleter.deleteAll();
    if (VL_UNLIKELY(!vlSymsp->__Vm_didInit)) {
        VL_DEBUG_IF(VL_DBG_MSGF("+ Initial\n"););
        VHive_FpAddNarrow___024root___eval_static(&(vlSymsp->TOP));
        VHive_FpAddNarrow___024root___eval_initial(&(vlSymsp->TOP));
        VHive_FpAddNarrow___024root___eval_settle(&(vlSymsp->TOP));
        vlSymsp->__Vm_didInit = true;
    }
    VL_DEBUG_IF(VL_DBG_MSGF("+ Eval\n"););
    VHive_FpAddNarrow___024root___eval(&(vlSymsp->TOP));
    // Evaluate cleanup
    Verilated::endOfEval(vlSymsp->__Vm_evalMsgQp);
}

//============================================================
// Events and timing
bool VHive_FpAddNarrow::eventsPending() { return false; }

uint64_t VHive_FpAddNarrow::nextTimeSlot() {
    VL_FATAL_MT(__FILE__, __LINE__, "", "No delays in the design");
    return 0;
}

//============================================================
// Utilities

const char* VHive_FpAddNarrow::name() const {
    return vlSymsp->name();
}

//============================================================
// Invoke final blocks

void VHive_FpAddNarrow___024root___eval_final(VHive_FpAddNarrow___024root* vlSelf);

VL_ATTR_COLD void VHive_FpAddNarrow::final() {
    contextp()->executingFinal(true);
    VHive_FpAddNarrow___024root___eval_final(&(vlSymsp->TOP));
    contextp()->executingFinal(false);
}

//============================================================
// Implementations of abstract methods from VerilatedModel

const char* VHive_FpAddNarrow::hierName() const { return vlSymsp->name(); }
const char* VHive_FpAddNarrow::modelName() const { return "VHive_FpAddNarrow"; }
unsigned VHive_FpAddNarrow::threads() const { return 1; }
void VHive_FpAddNarrow::prepareClone() const { contextp()->prepareClone(); }
void VHive_FpAddNarrow::atClone() const {
    contextp()->threadPoolpOnClone();
}
