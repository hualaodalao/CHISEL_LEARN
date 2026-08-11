// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Implementation of DPI export functions
//
// Verilator compiles this file in when DPI functions are used.
// If you have multiple Verilated designs with the same DPI exported
// function names, you will get multiple definition link errors from here.
// This is an unfortunate result of the DPI specification.
// To solve this, either
//    1. Call VsvsimTestbench::{export_function} instead,
//       and do not even bother to compile this file
// or 2. Compile all __Dpi.cpp files in the same compiler run,
//       and #ifdefs already inserted here will sort everything out.

#include "VsvsimTestbench__Dpi.h"
#include "VsvsimTestbench.h"

#ifndef VL_DPIDECL_getBitWidthImpl_clock_
#define VL_DPIDECL_getBitWidthImpl_clock_
void getBitWidthImpl_clock(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:103:17
    return VsvsimTestbench::getBitWidthImpl_clock(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_cmd_payload_data0_
#define VL_DPIDECL_getBitWidthImpl_io_cmd_payload_data0_
void getBitWidthImpl_io_cmd_payload_data0(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:663:17
    return VsvsimTestbench::getBitWidthImpl_io_cmd_payload_data0(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_cmd_payload_data1_
#define VL_DPIDECL_getBitWidthImpl_io_cmd_payload_data1_
void getBitWidthImpl_io_cmd_payload_data1(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:646:17
    return VsvsimTestbench::getBitWidthImpl_io_cmd_payload_data1(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_cmd_payload_op_
#define VL_DPIDECL_getBitWidthImpl_io_cmd_payload_op_
void getBitWidthImpl_io_cmd_payload_op(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:714:17
    return VsvsimTestbench::getBitWidthImpl_io_cmd_payload_op(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_cmd_payload_reg0Addr_
#define VL_DPIDECL_getBitWidthImpl_io_cmd_payload_reg0Addr_
void getBitWidthImpl_io_cmd_payload_reg0Addr(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:697:17
    return VsvsimTestbench::getBitWidthImpl_io_cmd_payload_reg0Addr(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_cmd_payload_reg1Addr_
#define VL_DPIDECL_getBitWidthImpl_io_cmd_payload_reg1Addr_
void getBitWidthImpl_io_cmd_payload_reg1Addr(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:680:17
    return VsvsimTestbench::getBitWidthImpl_io_cmd_payload_reg1Addr(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_cmd_ready_
#define VL_DPIDECL_getBitWidthImpl_io_cmd_ready_
void getBitWidthImpl_io_cmd_ready(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:731:17
    return VsvsimTestbench::getBitWidthImpl_io_cmd_ready(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_cmd_valid_
#define VL_DPIDECL_getBitWidthImpl_io_cmd_valid_
void getBitWidthImpl_io_cmd_valid(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:743:17
    return VsvsimTestbench::getBitWidthImpl_io_cmd_valid(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_dma0Ext_cmd_payload_addr_
#define VL_DPIDECL_getBitWidthImpl_io_dma0Ext_cmd_payload_addr_
void getBitWidthImpl_io_dma0Ext_cmd_payload_addr(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:477:17
    return VsvsimTestbench::getBitWidthImpl_io_dma0Ext_cmd_payload_addr(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_dma0Ext_cmd_ready_
#define VL_DPIDECL_getBitWidthImpl_io_dma0Ext_cmd_ready_
void getBitWidthImpl_io_dma0Ext_cmd_ready(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:489:17
    return VsvsimTestbench::getBitWidthImpl_io_dma0Ext_cmd_ready(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_dma0Ext_cmd_valid_
#define VL_DPIDECL_getBitWidthImpl_io_dma0Ext_cmd_valid_
void getBitWidthImpl_io_dma0Ext_cmd_valid(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:506:17
    return VsvsimTestbench::getBitWidthImpl_io_dma0Ext_cmd_valid(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_dma0Ext_rsp_payload_data_
#define VL_DPIDECL_getBitWidthImpl_io_dma0Ext_rsp_payload_data_
void getBitWidthImpl_io_dma0Ext_rsp_payload_data(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:535:17
    return VsvsimTestbench::getBitWidthImpl_io_dma0Ext_rsp_payload_data(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_dma0Ext_rsp_payload_rsp_
#define VL_DPIDECL_getBitWidthImpl_io_dma0Ext_rsp_payload_rsp_
void getBitWidthImpl_io_dma0Ext_rsp_payload_rsp(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:518:17
    return VsvsimTestbench::getBitWidthImpl_io_dma0Ext_rsp_payload_rsp(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_dma0Ext_rsp_ready_
#define VL_DPIDECL_getBitWidthImpl_io_dma0Ext_rsp_ready_
void getBitWidthImpl_io_dma0Ext_rsp_ready(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:552:17
    return VsvsimTestbench::getBitWidthImpl_io_dma0Ext_rsp_ready(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_dma0Ext_rsp_valid_
#define VL_DPIDECL_getBitWidthImpl_io_dma0Ext_rsp_valid_
void getBitWidthImpl_io_dma0Ext_rsp_valid(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:564:17
    return VsvsimTestbench::getBitWidthImpl_io_dma0Ext_rsp_valid(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_dma1Ext_addr_
#define VL_DPIDECL_getBitWidthImpl_io_dma1Ext_addr_
void getBitWidthImpl_io_dma1Ext_addr(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:378:17
    return VsvsimTestbench::getBitWidthImpl_io_dma1Ext_addr(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_dma1Ext_grant_
#define VL_DPIDECL_getBitWidthImpl_io_dma1Ext_grant_
void getBitWidthImpl_io_dma1Ext_grant(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:337:17
    return VsvsimTestbench::getBitWidthImpl_io_dma1Ext_grant(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_dma1Ext_isWrite_
#define VL_DPIDECL_getBitWidthImpl_io_dma1Ext_isWrite_
void getBitWidthImpl_io_dma1Ext_isWrite(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:325:17
    return VsvsimTestbench::getBitWidthImpl_io_dma1Ext_isWrite(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_dma1Ext_len_
#define VL_DPIDECL_getBitWidthImpl_io_dma1Ext_len_
void getBitWidthImpl_io_dma1Ext_len(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:366:17
    return VsvsimTestbench::getBitWidthImpl_io_dma1Ext_len(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_dma1Ext_readData_payload_
#define VL_DPIDECL_getBitWidthImpl_io_dma1Ext_readData_payload_
void getBitWidthImpl_io_dma1Ext_readData_payload(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:431:17
    return VsvsimTestbench::getBitWidthImpl_io_dma1Ext_readData_payload(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_dma1Ext_readData_ready_
#define VL_DPIDECL_getBitWidthImpl_io_dma1Ext_readData_ready_
void getBitWidthImpl_io_dma1Ext_readData_ready(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:448:17
    return VsvsimTestbench::getBitWidthImpl_io_dma1Ext_readData_ready(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_dma1Ext_readData_valid_
#define VL_DPIDECL_getBitWidthImpl_io_dma1Ext_readData_valid_
void getBitWidthImpl_io_dma1Ext_readData_valid(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:460:17
    return VsvsimTestbench::getBitWidthImpl_io_dma1Ext_readData_valid(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_dma1Ext_req_
#define VL_DPIDECL_getBitWidthImpl_io_dma1Ext_req_
void getBitWidthImpl_io_dma1Ext_req(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:354:17
    return VsvsimTestbench::getBitWidthImpl_io_dma1Ext_req(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_dma1Ext_writeData_payload_
#define VL_DPIDECL_getBitWidthImpl_io_dma1Ext_writeData_payload_
void getBitWidthImpl_io_dma1Ext_writeData_payload(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:390:17
    return VsvsimTestbench::getBitWidthImpl_io_dma1Ext_writeData_payload(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_dma1Ext_writeData_ready_
#define VL_DPIDECL_getBitWidthImpl_io_dma1Ext_writeData_ready_
void getBitWidthImpl_io_dma1Ext_writeData_ready(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:402:17
    return VsvsimTestbench::getBitWidthImpl_io_dma1Ext_writeData_ready(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_dma1Ext_writeData_valid_
#define VL_DPIDECL_getBitWidthImpl_io_dma1Ext_writeData_valid_
void getBitWidthImpl_io_dma1Ext_writeData_valid(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:419:17
    return VsvsimTestbench::getBitWidthImpl_io_dma1Ext_writeData_valid(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_dma2Ext_cmd_payload_addr_
#define VL_DPIDECL_getBitWidthImpl_io_dma2Ext_cmd_payload_addr_
void getBitWidthImpl_io_dma2Ext_cmd_payload_addr(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:221:17
    return VsvsimTestbench::getBitWidthImpl_io_dma2Ext_cmd_payload_addr(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_dma2Ext_cmd_ready_
#define VL_DPIDECL_getBitWidthImpl_io_dma2Ext_cmd_ready_
void getBitWidthImpl_io_dma2Ext_cmd_ready(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:233:17
    return VsvsimTestbench::getBitWidthImpl_io_dma2Ext_cmd_ready(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_dma2Ext_cmd_valid_
#define VL_DPIDECL_getBitWidthImpl_io_dma2Ext_cmd_valid_
void getBitWidthImpl_io_dma2Ext_cmd_valid(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:250:17
    return VsvsimTestbench::getBitWidthImpl_io_dma2Ext_cmd_valid(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_dma2Ext_rsp_payload_data_
#define VL_DPIDECL_getBitWidthImpl_io_dma2Ext_rsp_payload_data_
void getBitWidthImpl_io_dma2Ext_rsp_payload_data(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:279:17
    return VsvsimTestbench::getBitWidthImpl_io_dma2Ext_rsp_payload_data(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_dma2Ext_rsp_payload_rsp_
#define VL_DPIDECL_getBitWidthImpl_io_dma2Ext_rsp_payload_rsp_
void getBitWidthImpl_io_dma2Ext_rsp_payload_rsp(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:262:17
    return VsvsimTestbench::getBitWidthImpl_io_dma2Ext_rsp_payload_rsp(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_dma2Ext_rsp_ready_
#define VL_DPIDECL_getBitWidthImpl_io_dma2Ext_rsp_ready_
void getBitWidthImpl_io_dma2Ext_rsp_ready(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:296:17
    return VsvsimTestbench::getBitWidthImpl_io_dma2Ext_rsp_ready(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_dma2Ext_rsp_valid_
#define VL_DPIDECL_getBitWidthImpl_io_dma2Ext_rsp_valid_
void getBitWidthImpl_io_dma2Ext_rsp_valid(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:308:17
    return VsvsimTestbench::getBitWidthImpl_io_dma2Ext_rsp_valid(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_resp_payload_data_
#define VL_DPIDECL_getBitWidthImpl_io_resp_payload_data_
void getBitWidthImpl_io_resp_payload_data(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:605:17
    return VsvsimTestbench::getBitWidthImpl_io_resp_payload_data(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_resp_payload_done_
#define VL_DPIDECL_getBitWidthImpl_io_resp_payload_done_
void getBitWidthImpl_io_resp_payload_done(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:593:17
    return VsvsimTestbench::getBitWidthImpl_io_resp_payload_done(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_resp_payload_err_
#define VL_DPIDECL_getBitWidthImpl_io_resp_payload_err_
void getBitWidthImpl_io_resp_payload_err(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:581:17
    return VsvsimTestbench::getBitWidthImpl_io_resp_payload_err(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_resp_ready_
#define VL_DPIDECL_getBitWidthImpl_io_resp_ready_
void getBitWidthImpl_io_resp_ready(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:617:17
    return VsvsimTestbench::getBitWidthImpl_io_resp_ready(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_resp_valid_
#define VL_DPIDECL_getBitWidthImpl_io_resp_valid_
void getBitWidthImpl_io_resp_valid(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:634:17
    return VsvsimTestbench::getBitWidthImpl_io_resp_valid(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_status_aOccupancy_
#define VL_DPIDECL_getBitWidthImpl_io_status_aOccupancy_
void getBitWidthImpl_io_status_aOccupancy(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:161:17
    return VsvsimTestbench::getBitWidthImpl_io_status_aOccupancy(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_status_bOccupancy_
#define VL_DPIDECL_getBitWidthImpl_io_status_bOccupancy_
void getBitWidthImpl_io_status_bOccupancy(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:149:17
    return VsvsimTestbench::getBitWidthImpl_io_status_bOccupancy(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_status_busy_
#define VL_DPIDECL_getBitWidthImpl_io_status_busy_
void getBitWidthImpl_io_status_busy(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:209:17
    return VsvsimTestbench::getBitWidthImpl_io_status_busy(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_status_cOccupancy_
#define VL_DPIDECL_getBitWidthImpl_io_status_cOccupancy_
void getBitWidthImpl_io_status_cOccupancy(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:137:17
    return VsvsimTestbench::getBitWidthImpl_io_status_cOccupancy(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_status_done_
#define VL_DPIDECL_getBitWidthImpl_io_status_done_
void getBitWidthImpl_io_status_done(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:197:17
    return VsvsimTestbench::getBitWidthImpl_io_status_done(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_status_err_
#define VL_DPIDECL_getBitWidthImpl_io_status_err_
void getBitWidthImpl_io_status_err(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:185:17
    return VsvsimTestbench::getBitWidthImpl_io_status_err(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_io_status_progress_
#define VL_DPIDECL_getBitWidthImpl_io_status_progress_
void getBitWidthImpl_io_status_progress(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:173:17
    return VsvsimTestbench::getBitWidthImpl_io_status_progress(value);
}
#endif

#ifndef VL_DPIDECL_getBitWidthImpl_reset_
#define VL_DPIDECL_getBitWidthImpl_reset_
void getBitWidthImpl_reset(int* value) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:120:17
    return VsvsimTestbench::getBitWidthImpl_reset(value);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_clock_
#define VL_DPIDECL_getBitsImpl_clock_
void getBitsImpl_clock(svBitVecVal* value_clock) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:113:17
    return VsvsimTestbench::getBitsImpl_clock(value_clock);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_cmd_payload_data0_
#define VL_DPIDECL_getBitsImpl_io_cmd_payload_data0_
void getBitsImpl_io_cmd_payload_data0(svBitVecVal* value_io_cmd_payload_data0) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:673:17
    return VsvsimTestbench::getBitsImpl_io_cmd_payload_data0(value_io_cmd_payload_data0);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_cmd_payload_data1_
#define VL_DPIDECL_getBitsImpl_io_cmd_payload_data1_
void getBitsImpl_io_cmd_payload_data1(svBitVecVal* value_io_cmd_payload_data1) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:656:17
    return VsvsimTestbench::getBitsImpl_io_cmd_payload_data1(value_io_cmd_payload_data1);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_cmd_payload_op_
#define VL_DPIDECL_getBitsImpl_io_cmd_payload_op_
void getBitsImpl_io_cmd_payload_op(svBitVecVal* value_io_cmd_payload_op) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:724:17
    return VsvsimTestbench::getBitsImpl_io_cmd_payload_op(value_io_cmd_payload_op);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_cmd_payload_reg0Addr_
#define VL_DPIDECL_getBitsImpl_io_cmd_payload_reg0Addr_
void getBitsImpl_io_cmd_payload_reg0Addr(svBitVecVal* value_io_cmd_payload_reg0Addr) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:707:17
    return VsvsimTestbench::getBitsImpl_io_cmd_payload_reg0Addr(value_io_cmd_payload_reg0Addr);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_cmd_payload_reg1Addr_
#define VL_DPIDECL_getBitsImpl_io_cmd_payload_reg1Addr_
void getBitsImpl_io_cmd_payload_reg1Addr(svBitVecVal* value_io_cmd_payload_reg1Addr) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:690:17
    return VsvsimTestbench::getBitsImpl_io_cmd_payload_reg1Addr(value_io_cmd_payload_reg1Addr);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_cmd_ready_
#define VL_DPIDECL_getBitsImpl_io_cmd_ready_
void getBitsImpl_io_cmd_ready(svBitVecVal* value_io_cmd_ready) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:736:17
    return VsvsimTestbench::getBitsImpl_io_cmd_ready(value_io_cmd_ready);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_cmd_valid_
#define VL_DPIDECL_getBitsImpl_io_cmd_valid_
void getBitsImpl_io_cmd_valid(svBitVecVal* value_io_cmd_valid) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:753:17
    return VsvsimTestbench::getBitsImpl_io_cmd_valid(value_io_cmd_valid);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_dma0Ext_cmd_payload_addr_
#define VL_DPIDECL_getBitsImpl_io_dma0Ext_cmd_payload_addr_
void getBitsImpl_io_dma0Ext_cmd_payload_addr(svBitVecVal* value_io_dma0Ext_cmd_payload_addr) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:482:17
    return VsvsimTestbench::getBitsImpl_io_dma0Ext_cmd_payload_addr(value_io_dma0Ext_cmd_payload_addr);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_dma0Ext_cmd_ready_
#define VL_DPIDECL_getBitsImpl_io_dma0Ext_cmd_ready_
void getBitsImpl_io_dma0Ext_cmd_ready(svBitVecVal* value_io_dma0Ext_cmd_ready) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:499:17
    return VsvsimTestbench::getBitsImpl_io_dma0Ext_cmd_ready(value_io_dma0Ext_cmd_ready);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_dma0Ext_cmd_valid_
#define VL_DPIDECL_getBitsImpl_io_dma0Ext_cmd_valid_
void getBitsImpl_io_dma0Ext_cmd_valid(svBitVecVal* value_io_dma0Ext_cmd_valid) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:511:17
    return VsvsimTestbench::getBitsImpl_io_dma0Ext_cmd_valid(value_io_dma0Ext_cmd_valid);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_dma0Ext_rsp_payload_data_
#define VL_DPIDECL_getBitsImpl_io_dma0Ext_rsp_payload_data_
void getBitsImpl_io_dma0Ext_rsp_payload_data(svBitVecVal* value_io_dma0Ext_rsp_payload_data) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:545:17
    return VsvsimTestbench::getBitsImpl_io_dma0Ext_rsp_payload_data(value_io_dma0Ext_rsp_payload_data);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_dma0Ext_rsp_payload_rsp_
#define VL_DPIDECL_getBitsImpl_io_dma0Ext_rsp_payload_rsp_
void getBitsImpl_io_dma0Ext_rsp_payload_rsp(svBitVecVal* value_io_dma0Ext_rsp_payload_rsp) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:528:17
    return VsvsimTestbench::getBitsImpl_io_dma0Ext_rsp_payload_rsp(value_io_dma0Ext_rsp_payload_rsp);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_dma0Ext_rsp_ready_
#define VL_DPIDECL_getBitsImpl_io_dma0Ext_rsp_ready_
void getBitsImpl_io_dma0Ext_rsp_ready(svBitVecVal* value_io_dma0Ext_rsp_ready) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:557:17
    return VsvsimTestbench::getBitsImpl_io_dma0Ext_rsp_ready(value_io_dma0Ext_rsp_ready);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_dma0Ext_rsp_valid_
#define VL_DPIDECL_getBitsImpl_io_dma0Ext_rsp_valid_
void getBitsImpl_io_dma0Ext_rsp_valid(svBitVecVal* value_io_dma0Ext_rsp_valid) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:574:17
    return VsvsimTestbench::getBitsImpl_io_dma0Ext_rsp_valid(value_io_dma0Ext_rsp_valid);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_dma1Ext_addr_
#define VL_DPIDECL_getBitsImpl_io_dma1Ext_addr_
void getBitsImpl_io_dma1Ext_addr(svBitVecVal* value_io_dma1Ext_addr) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:383:17
    return VsvsimTestbench::getBitsImpl_io_dma1Ext_addr(value_io_dma1Ext_addr);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_dma1Ext_grant_
#define VL_DPIDECL_getBitsImpl_io_dma1Ext_grant_
void getBitsImpl_io_dma1Ext_grant(svBitVecVal* value_io_dma1Ext_grant) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:347:17
    return VsvsimTestbench::getBitsImpl_io_dma1Ext_grant(value_io_dma1Ext_grant);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_dma1Ext_isWrite_
#define VL_DPIDECL_getBitsImpl_io_dma1Ext_isWrite_
void getBitsImpl_io_dma1Ext_isWrite(svBitVecVal* value_io_dma1Ext_isWrite) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:330:17
    return VsvsimTestbench::getBitsImpl_io_dma1Ext_isWrite(value_io_dma1Ext_isWrite);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_dma1Ext_len_
#define VL_DPIDECL_getBitsImpl_io_dma1Ext_len_
void getBitsImpl_io_dma1Ext_len(svBitVecVal* value_io_dma1Ext_len) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:371:17
    return VsvsimTestbench::getBitsImpl_io_dma1Ext_len(value_io_dma1Ext_len);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_dma1Ext_readData_payload_
#define VL_DPIDECL_getBitsImpl_io_dma1Ext_readData_payload_
void getBitsImpl_io_dma1Ext_readData_payload(svBitVecVal* value_io_dma1Ext_readData_payload) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:441:17
    return VsvsimTestbench::getBitsImpl_io_dma1Ext_readData_payload(value_io_dma1Ext_readData_payload);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_dma1Ext_readData_ready_
#define VL_DPIDECL_getBitsImpl_io_dma1Ext_readData_ready_
void getBitsImpl_io_dma1Ext_readData_ready(svBitVecVal* value_io_dma1Ext_readData_ready) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:453:17
    return VsvsimTestbench::getBitsImpl_io_dma1Ext_readData_ready(value_io_dma1Ext_readData_ready);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_dma1Ext_readData_valid_
#define VL_DPIDECL_getBitsImpl_io_dma1Ext_readData_valid_
void getBitsImpl_io_dma1Ext_readData_valid(svBitVecVal* value_io_dma1Ext_readData_valid) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:470:17
    return VsvsimTestbench::getBitsImpl_io_dma1Ext_readData_valid(value_io_dma1Ext_readData_valid);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_dma1Ext_req_
#define VL_DPIDECL_getBitsImpl_io_dma1Ext_req_
void getBitsImpl_io_dma1Ext_req(svBitVecVal* value_io_dma1Ext_req) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:359:17
    return VsvsimTestbench::getBitsImpl_io_dma1Ext_req(value_io_dma1Ext_req);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_dma1Ext_writeData_payload_
#define VL_DPIDECL_getBitsImpl_io_dma1Ext_writeData_payload_
void getBitsImpl_io_dma1Ext_writeData_payload(svBitVecVal* value_io_dma1Ext_writeData_payload) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:395:17
    return VsvsimTestbench::getBitsImpl_io_dma1Ext_writeData_payload(value_io_dma1Ext_writeData_payload);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_dma1Ext_writeData_ready_
#define VL_DPIDECL_getBitsImpl_io_dma1Ext_writeData_ready_
void getBitsImpl_io_dma1Ext_writeData_ready(svBitVecVal* value_io_dma1Ext_writeData_ready) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:412:17
    return VsvsimTestbench::getBitsImpl_io_dma1Ext_writeData_ready(value_io_dma1Ext_writeData_ready);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_dma1Ext_writeData_valid_
#define VL_DPIDECL_getBitsImpl_io_dma1Ext_writeData_valid_
void getBitsImpl_io_dma1Ext_writeData_valid(svBitVecVal* value_io_dma1Ext_writeData_valid) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:424:17
    return VsvsimTestbench::getBitsImpl_io_dma1Ext_writeData_valid(value_io_dma1Ext_writeData_valid);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_dma2Ext_cmd_payload_addr_
#define VL_DPIDECL_getBitsImpl_io_dma2Ext_cmd_payload_addr_
void getBitsImpl_io_dma2Ext_cmd_payload_addr(svBitVecVal* value_io_dma2Ext_cmd_payload_addr) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:226:17
    return VsvsimTestbench::getBitsImpl_io_dma2Ext_cmd_payload_addr(value_io_dma2Ext_cmd_payload_addr);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_dma2Ext_cmd_ready_
#define VL_DPIDECL_getBitsImpl_io_dma2Ext_cmd_ready_
void getBitsImpl_io_dma2Ext_cmd_ready(svBitVecVal* value_io_dma2Ext_cmd_ready) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:243:17
    return VsvsimTestbench::getBitsImpl_io_dma2Ext_cmd_ready(value_io_dma2Ext_cmd_ready);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_dma2Ext_cmd_valid_
#define VL_DPIDECL_getBitsImpl_io_dma2Ext_cmd_valid_
void getBitsImpl_io_dma2Ext_cmd_valid(svBitVecVal* value_io_dma2Ext_cmd_valid) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:255:17
    return VsvsimTestbench::getBitsImpl_io_dma2Ext_cmd_valid(value_io_dma2Ext_cmd_valid);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_dma2Ext_rsp_payload_data_
#define VL_DPIDECL_getBitsImpl_io_dma2Ext_rsp_payload_data_
void getBitsImpl_io_dma2Ext_rsp_payload_data(svBitVecVal* value_io_dma2Ext_rsp_payload_data) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:289:17
    return VsvsimTestbench::getBitsImpl_io_dma2Ext_rsp_payload_data(value_io_dma2Ext_rsp_payload_data);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_dma2Ext_rsp_payload_rsp_
#define VL_DPIDECL_getBitsImpl_io_dma2Ext_rsp_payload_rsp_
void getBitsImpl_io_dma2Ext_rsp_payload_rsp(svBitVecVal* value_io_dma2Ext_rsp_payload_rsp) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:272:17
    return VsvsimTestbench::getBitsImpl_io_dma2Ext_rsp_payload_rsp(value_io_dma2Ext_rsp_payload_rsp);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_dma2Ext_rsp_ready_
#define VL_DPIDECL_getBitsImpl_io_dma2Ext_rsp_ready_
void getBitsImpl_io_dma2Ext_rsp_ready(svBitVecVal* value_io_dma2Ext_rsp_ready) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:301:17
    return VsvsimTestbench::getBitsImpl_io_dma2Ext_rsp_ready(value_io_dma2Ext_rsp_ready);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_dma2Ext_rsp_valid_
#define VL_DPIDECL_getBitsImpl_io_dma2Ext_rsp_valid_
void getBitsImpl_io_dma2Ext_rsp_valid(svBitVecVal* value_io_dma2Ext_rsp_valid) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:318:17
    return VsvsimTestbench::getBitsImpl_io_dma2Ext_rsp_valid(value_io_dma2Ext_rsp_valid);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_resp_payload_data_
#define VL_DPIDECL_getBitsImpl_io_resp_payload_data_
void getBitsImpl_io_resp_payload_data(svBitVecVal* value_io_resp_payload_data) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:610:17
    return VsvsimTestbench::getBitsImpl_io_resp_payload_data(value_io_resp_payload_data);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_resp_payload_done_
#define VL_DPIDECL_getBitsImpl_io_resp_payload_done_
void getBitsImpl_io_resp_payload_done(svBitVecVal* value_io_resp_payload_done) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:598:17
    return VsvsimTestbench::getBitsImpl_io_resp_payload_done(value_io_resp_payload_done);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_resp_payload_err_
#define VL_DPIDECL_getBitsImpl_io_resp_payload_err_
void getBitsImpl_io_resp_payload_err(svBitVecVal* value_io_resp_payload_err) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:586:17
    return VsvsimTestbench::getBitsImpl_io_resp_payload_err(value_io_resp_payload_err);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_resp_ready_
#define VL_DPIDECL_getBitsImpl_io_resp_ready_
void getBitsImpl_io_resp_ready(svBitVecVal* value_io_resp_ready) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:627:17
    return VsvsimTestbench::getBitsImpl_io_resp_ready(value_io_resp_ready);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_resp_valid_
#define VL_DPIDECL_getBitsImpl_io_resp_valid_
void getBitsImpl_io_resp_valid(svBitVecVal* value_io_resp_valid) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:639:17
    return VsvsimTestbench::getBitsImpl_io_resp_valid(value_io_resp_valid);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_status_aOccupancy_
#define VL_DPIDECL_getBitsImpl_io_status_aOccupancy_
void getBitsImpl_io_status_aOccupancy(svBitVecVal* value_io_status_aOccupancy) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:166:17
    return VsvsimTestbench::getBitsImpl_io_status_aOccupancy(value_io_status_aOccupancy);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_status_bOccupancy_
#define VL_DPIDECL_getBitsImpl_io_status_bOccupancy_
void getBitsImpl_io_status_bOccupancy(svBitVecVal* value_io_status_bOccupancy) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:154:17
    return VsvsimTestbench::getBitsImpl_io_status_bOccupancy(value_io_status_bOccupancy);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_status_busy_
#define VL_DPIDECL_getBitsImpl_io_status_busy_
void getBitsImpl_io_status_busy(svBitVecVal* value_io_status_busy) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:214:17
    return VsvsimTestbench::getBitsImpl_io_status_busy(value_io_status_busy);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_status_cOccupancy_
#define VL_DPIDECL_getBitsImpl_io_status_cOccupancy_
void getBitsImpl_io_status_cOccupancy(svBitVecVal* value_io_status_cOccupancy) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:142:17
    return VsvsimTestbench::getBitsImpl_io_status_cOccupancy(value_io_status_cOccupancy);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_status_done_
#define VL_DPIDECL_getBitsImpl_io_status_done_
void getBitsImpl_io_status_done(svBitVecVal* value_io_status_done) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:202:17
    return VsvsimTestbench::getBitsImpl_io_status_done(value_io_status_done);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_status_err_
#define VL_DPIDECL_getBitsImpl_io_status_err_
void getBitsImpl_io_status_err(svBitVecVal* value_io_status_err) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:190:17
    return VsvsimTestbench::getBitsImpl_io_status_err(value_io_status_err);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_io_status_progress_
#define VL_DPIDECL_getBitsImpl_io_status_progress_
void getBitsImpl_io_status_progress(svBitVecVal* value_io_status_progress) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:178:17
    return VsvsimTestbench::getBitsImpl_io_status_progress(value_io_status_progress);
}
#endif

#ifndef VL_DPIDECL_getBitsImpl_reset_
#define VL_DPIDECL_getBitsImpl_reset_
void getBitsImpl_reset(svBitVecVal* value_reset) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:130:17
    return VsvsimTestbench::getBitsImpl_reset(value_reset);
}
#endif

#ifndef VL_DPIDECL_setBitsImpl_clock_
#define VL_DPIDECL_setBitsImpl_clock_
void setBitsImpl_clock(const svBitVecVal* value_clock) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:108:17
    return VsvsimTestbench::setBitsImpl_clock(value_clock);
}
#endif

#ifndef VL_DPIDECL_setBitsImpl_io_cmd_payload_data0_
#define VL_DPIDECL_setBitsImpl_io_cmd_payload_data0_
void setBitsImpl_io_cmd_payload_data0(const svBitVecVal* value_io_cmd_payload_data0) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:668:17
    return VsvsimTestbench::setBitsImpl_io_cmd_payload_data0(value_io_cmd_payload_data0);
}
#endif

#ifndef VL_DPIDECL_setBitsImpl_io_cmd_payload_data1_
#define VL_DPIDECL_setBitsImpl_io_cmd_payload_data1_
void setBitsImpl_io_cmd_payload_data1(const svBitVecVal* value_io_cmd_payload_data1) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:651:17
    return VsvsimTestbench::setBitsImpl_io_cmd_payload_data1(value_io_cmd_payload_data1);
}
#endif

#ifndef VL_DPIDECL_setBitsImpl_io_cmd_payload_op_
#define VL_DPIDECL_setBitsImpl_io_cmd_payload_op_
void setBitsImpl_io_cmd_payload_op(const svBitVecVal* value_io_cmd_payload_op) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:719:17
    return VsvsimTestbench::setBitsImpl_io_cmd_payload_op(value_io_cmd_payload_op);
}
#endif

#ifndef VL_DPIDECL_setBitsImpl_io_cmd_payload_reg0Addr_
#define VL_DPIDECL_setBitsImpl_io_cmd_payload_reg0Addr_
void setBitsImpl_io_cmd_payload_reg0Addr(const svBitVecVal* value_io_cmd_payload_reg0Addr) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:702:17
    return VsvsimTestbench::setBitsImpl_io_cmd_payload_reg0Addr(value_io_cmd_payload_reg0Addr);
}
#endif

#ifndef VL_DPIDECL_setBitsImpl_io_cmd_payload_reg1Addr_
#define VL_DPIDECL_setBitsImpl_io_cmd_payload_reg1Addr_
void setBitsImpl_io_cmd_payload_reg1Addr(const svBitVecVal* value_io_cmd_payload_reg1Addr) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:685:17
    return VsvsimTestbench::setBitsImpl_io_cmd_payload_reg1Addr(value_io_cmd_payload_reg1Addr);
}
#endif

#ifndef VL_DPIDECL_setBitsImpl_io_cmd_valid_
#define VL_DPIDECL_setBitsImpl_io_cmd_valid_
void setBitsImpl_io_cmd_valid(const svBitVecVal* value_io_cmd_valid) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:748:17
    return VsvsimTestbench::setBitsImpl_io_cmd_valid(value_io_cmd_valid);
}
#endif

#ifndef VL_DPIDECL_setBitsImpl_io_dma0Ext_cmd_ready_
#define VL_DPIDECL_setBitsImpl_io_dma0Ext_cmd_ready_
void setBitsImpl_io_dma0Ext_cmd_ready(const svBitVecVal* value_io_dma0Ext_cmd_ready) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:494:17
    return VsvsimTestbench::setBitsImpl_io_dma0Ext_cmd_ready(value_io_dma0Ext_cmd_ready);
}
#endif

#ifndef VL_DPIDECL_setBitsImpl_io_dma0Ext_rsp_payload_data_
#define VL_DPIDECL_setBitsImpl_io_dma0Ext_rsp_payload_data_
void setBitsImpl_io_dma0Ext_rsp_payload_data(const svBitVecVal* value_io_dma0Ext_rsp_payload_data) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:540:17
    return VsvsimTestbench::setBitsImpl_io_dma0Ext_rsp_payload_data(value_io_dma0Ext_rsp_payload_data);
}
#endif

#ifndef VL_DPIDECL_setBitsImpl_io_dma0Ext_rsp_payload_rsp_
#define VL_DPIDECL_setBitsImpl_io_dma0Ext_rsp_payload_rsp_
void setBitsImpl_io_dma0Ext_rsp_payload_rsp(const svBitVecVal* value_io_dma0Ext_rsp_payload_rsp) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:523:17
    return VsvsimTestbench::setBitsImpl_io_dma0Ext_rsp_payload_rsp(value_io_dma0Ext_rsp_payload_rsp);
}
#endif

#ifndef VL_DPIDECL_setBitsImpl_io_dma0Ext_rsp_valid_
#define VL_DPIDECL_setBitsImpl_io_dma0Ext_rsp_valid_
void setBitsImpl_io_dma0Ext_rsp_valid(const svBitVecVal* value_io_dma0Ext_rsp_valid) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:569:17
    return VsvsimTestbench::setBitsImpl_io_dma0Ext_rsp_valid(value_io_dma0Ext_rsp_valid);
}
#endif

#ifndef VL_DPIDECL_setBitsImpl_io_dma1Ext_grant_
#define VL_DPIDECL_setBitsImpl_io_dma1Ext_grant_
void setBitsImpl_io_dma1Ext_grant(const svBitVecVal* value_io_dma1Ext_grant) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:342:17
    return VsvsimTestbench::setBitsImpl_io_dma1Ext_grant(value_io_dma1Ext_grant);
}
#endif

#ifndef VL_DPIDECL_setBitsImpl_io_dma1Ext_readData_payload_
#define VL_DPIDECL_setBitsImpl_io_dma1Ext_readData_payload_
void setBitsImpl_io_dma1Ext_readData_payload(const svBitVecVal* value_io_dma1Ext_readData_payload) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:436:17
    return VsvsimTestbench::setBitsImpl_io_dma1Ext_readData_payload(value_io_dma1Ext_readData_payload);
}
#endif

#ifndef VL_DPIDECL_setBitsImpl_io_dma1Ext_readData_valid_
#define VL_DPIDECL_setBitsImpl_io_dma1Ext_readData_valid_
void setBitsImpl_io_dma1Ext_readData_valid(const svBitVecVal* value_io_dma1Ext_readData_valid) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:465:17
    return VsvsimTestbench::setBitsImpl_io_dma1Ext_readData_valid(value_io_dma1Ext_readData_valid);
}
#endif

#ifndef VL_DPIDECL_setBitsImpl_io_dma1Ext_writeData_ready_
#define VL_DPIDECL_setBitsImpl_io_dma1Ext_writeData_ready_
void setBitsImpl_io_dma1Ext_writeData_ready(const svBitVecVal* value_io_dma1Ext_writeData_ready) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:407:17
    return VsvsimTestbench::setBitsImpl_io_dma1Ext_writeData_ready(value_io_dma1Ext_writeData_ready);
}
#endif

#ifndef VL_DPIDECL_setBitsImpl_io_dma2Ext_cmd_ready_
#define VL_DPIDECL_setBitsImpl_io_dma2Ext_cmd_ready_
void setBitsImpl_io_dma2Ext_cmd_ready(const svBitVecVal* value_io_dma2Ext_cmd_ready) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:238:17
    return VsvsimTestbench::setBitsImpl_io_dma2Ext_cmd_ready(value_io_dma2Ext_cmd_ready);
}
#endif

#ifndef VL_DPIDECL_setBitsImpl_io_dma2Ext_rsp_payload_data_
#define VL_DPIDECL_setBitsImpl_io_dma2Ext_rsp_payload_data_
void setBitsImpl_io_dma2Ext_rsp_payload_data(const svBitVecVal* value_io_dma2Ext_rsp_payload_data) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:284:17
    return VsvsimTestbench::setBitsImpl_io_dma2Ext_rsp_payload_data(value_io_dma2Ext_rsp_payload_data);
}
#endif

#ifndef VL_DPIDECL_setBitsImpl_io_dma2Ext_rsp_payload_rsp_
#define VL_DPIDECL_setBitsImpl_io_dma2Ext_rsp_payload_rsp_
void setBitsImpl_io_dma2Ext_rsp_payload_rsp(const svBitVecVal* value_io_dma2Ext_rsp_payload_rsp) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:267:17
    return VsvsimTestbench::setBitsImpl_io_dma2Ext_rsp_payload_rsp(value_io_dma2Ext_rsp_payload_rsp);
}
#endif

#ifndef VL_DPIDECL_setBitsImpl_io_dma2Ext_rsp_valid_
#define VL_DPIDECL_setBitsImpl_io_dma2Ext_rsp_valid_
void setBitsImpl_io_dma2Ext_rsp_valid(const svBitVecVal* value_io_dma2Ext_rsp_valid) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:313:17
    return VsvsimTestbench::setBitsImpl_io_dma2Ext_rsp_valid(value_io_dma2Ext_rsp_valid);
}
#endif

#ifndef VL_DPIDECL_setBitsImpl_io_resp_ready_
#define VL_DPIDECL_setBitsImpl_io_resp_ready_
void setBitsImpl_io_resp_ready(const svBitVecVal* value_io_resp_ready) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:622:17
    return VsvsimTestbench::setBitsImpl_io_resp_ready(value_io_resp_ready);
}
#endif

#ifndef VL_DPIDECL_setBitsImpl_reset_
#define VL_DPIDECL_setBitsImpl_reset_
void setBitsImpl_reset(const svBitVecVal* value_reset) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:125:17
    return VsvsimTestbench::setBitsImpl_reset(value_reset);
}
#endif

#ifndef VL_DPIDECL_simulation_disableTrace_
#define VL_DPIDECL_simulation_disableTrace_
void simulation_disableTrace(int* success) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:826:17
    return VsvsimTestbench::simulation_disableTrace(success);
}
#endif

#ifndef VL_DPIDECL_simulation_enableTrace_
#define VL_DPIDECL_simulation_enableTrace_
void simulation_enableTrace(int* success) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:811:17
    return VsvsimTestbench::simulation_enableTrace(success);
}
#endif

#ifndef VL_DPIDECL_simulation_initializeTrace_
#define VL_DPIDECL_simulation_initializeTrace_
void simulation_initializeTrace(const char* traceFilePath) {
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-compute-exact-C-A-B-for-M-32-N-16-K-32--2-M-tiles-x-2-K-passes--in-FP16/workdir-verilator/../generated-sources/testbench.sv:787:17
    return VsvsimTestbench::simulation_initializeTrace(traceFilePath);
}
#endif

