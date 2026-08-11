// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Prototypes for DPI import and export functions.
//
// Verilator includes this file in all generated .cpp files that use DPI functions.
// Manually include this file where DPI .c import functions are declared to ensure
// the C functions match the expectations of the DPI imports.

#ifndef VERILATED_VSVSIMTESTBENCH__DPI_H_
#define VERILATED_VSVSIMTESTBENCH__DPI_H_  // guard

#include "svdpi.h"

#ifdef __cplusplus
extern "C" {
#endif


    // DPI EXPORTS
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:103:17
    extern void getBitWidthImpl_clock(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:663:17
    extern void getBitWidthImpl_io_cmd_payload_data0(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:646:17
    extern void getBitWidthImpl_io_cmd_payload_data1(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:714:17
    extern void getBitWidthImpl_io_cmd_payload_op(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:697:17
    extern void getBitWidthImpl_io_cmd_payload_reg0Addr(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:680:17
    extern void getBitWidthImpl_io_cmd_payload_reg1Addr(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:731:17
    extern void getBitWidthImpl_io_cmd_ready(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:743:17
    extern void getBitWidthImpl_io_cmd_valid(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:477:17
    extern void getBitWidthImpl_io_dma0Ext_cmd_payload_addr(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:489:17
    extern void getBitWidthImpl_io_dma0Ext_cmd_ready(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:506:17
    extern void getBitWidthImpl_io_dma0Ext_cmd_valid(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:535:17
    extern void getBitWidthImpl_io_dma0Ext_rsp_payload_data(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:518:17
    extern void getBitWidthImpl_io_dma0Ext_rsp_payload_rsp(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:552:17
    extern void getBitWidthImpl_io_dma0Ext_rsp_ready(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:564:17
    extern void getBitWidthImpl_io_dma0Ext_rsp_valid(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:378:17
    extern void getBitWidthImpl_io_dma1Ext_addr(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:337:17
    extern void getBitWidthImpl_io_dma1Ext_grant(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:325:17
    extern void getBitWidthImpl_io_dma1Ext_isWrite(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:366:17
    extern void getBitWidthImpl_io_dma1Ext_len(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:431:17
    extern void getBitWidthImpl_io_dma1Ext_readData_payload(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:448:17
    extern void getBitWidthImpl_io_dma1Ext_readData_ready(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:460:17
    extern void getBitWidthImpl_io_dma1Ext_readData_valid(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:354:17
    extern void getBitWidthImpl_io_dma1Ext_req(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:390:17
    extern void getBitWidthImpl_io_dma1Ext_writeData_payload(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:402:17
    extern void getBitWidthImpl_io_dma1Ext_writeData_ready(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:419:17
    extern void getBitWidthImpl_io_dma1Ext_writeData_valid(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:221:17
    extern void getBitWidthImpl_io_dma2Ext_cmd_payload_addr(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:233:17
    extern void getBitWidthImpl_io_dma2Ext_cmd_ready(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:250:17
    extern void getBitWidthImpl_io_dma2Ext_cmd_valid(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:279:17
    extern void getBitWidthImpl_io_dma2Ext_rsp_payload_data(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:262:17
    extern void getBitWidthImpl_io_dma2Ext_rsp_payload_rsp(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:296:17
    extern void getBitWidthImpl_io_dma2Ext_rsp_ready(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:308:17
    extern void getBitWidthImpl_io_dma2Ext_rsp_valid(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:605:17
    extern void getBitWidthImpl_io_resp_payload_data(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:593:17
    extern void getBitWidthImpl_io_resp_payload_done(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:581:17
    extern void getBitWidthImpl_io_resp_payload_err(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:617:17
    extern void getBitWidthImpl_io_resp_ready(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:634:17
    extern void getBitWidthImpl_io_resp_valid(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:161:17
    extern void getBitWidthImpl_io_status_aOccupancy(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:149:17
    extern void getBitWidthImpl_io_status_bOccupancy(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:209:17
    extern void getBitWidthImpl_io_status_busy(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:137:17
    extern void getBitWidthImpl_io_status_cOccupancy(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:197:17
    extern void getBitWidthImpl_io_status_done(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:185:17
    extern void getBitWidthImpl_io_status_err(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:173:17
    extern void getBitWidthImpl_io_status_progress(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:120:17
    extern void getBitWidthImpl_reset(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:113:17
    extern void getBitsImpl_clock(svBitVecVal* value_clock);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:673:17
    extern void getBitsImpl_io_cmd_payload_data0(svBitVecVal* value_io_cmd_payload_data0);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:656:17
    extern void getBitsImpl_io_cmd_payload_data1(svBitVecVal* value_io_cmd_payload_data1);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:724:17
    extern void getBitsImpl_io_cmd_payload_op(svBitVecVal* value_io_cmd_payload_op);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:707:17
    extern void getBitsImpl_io_cmd_payload_reg0Addr(svBitVecVal* value_io_cmd_payload_reg0Addr);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:690:17
    extern void getBitsImpl_io_cmd_payload_reg1Addr(svBitVecVal* value_io_cmd_payload_reg1Addr);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:736:17
    extern void getBitsImpl_io_cmd_ready(svBitVecVal* value_io_cmd_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:753:17
    extern void getBitsImpl_io_cmd_valid(svBitVecVal* value_io_cmd_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:482:17
    extern void getBitsImpl_io_dma0Ext_cmd_payload_addr(svBitVecVal* value_io_dma0Ext_cmd_payload_addr);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:499:17
    extern void getBitsImpl_io_dma0Ext_cmd_ready(svBitVecVal* value_io_dma0Ext_cmd_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:511:17
    extern void getBitsImpl_io_dma0Ext_cmd_valid(svBitVecVal* value_io_dma0Ext_cmd_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:545:17
    extern void getBitsImpl_io_dma0Ext_rsp_payload_data(svBitVecVal* value_io_dma0Ext_rsp_payload_data);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:528:17
    extern void getBitsImpl_io_dma0Ext_rsp_payload_rsp(svBitVecVal* value_io_dma0Ext_rsp_payload_rsp);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:557:17
    extern void getBitsImpl_io_dma0Ext_rsp_ready(svBitVecVal* value_io_dma0Ext_rsp_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:574:17
    extern void getBitsImpl_io_dma0Ext_rsp_valid(svBitVecVal* value_io_dma0Ext_rsp_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:383:17
    extern void getBitsImpl_io_dma1Ext_addr(svBitVecVal* value_io_dma1Ext_addr);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:347:17
    extern void getBitsImpl_io_dma1Ext_grant(svBitVecVal* value_io_dma1Ext_grant);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:330:17
    extern void getBitsImpl_io_dma1Ext_isWrite(svBitVecVal* value_io_dma1Ext_isWrite);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:371:17
    extern void getBitsImpl_io_dma1Ext_len(svBitVecVal* value_io_dma1Ext_len);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:441:17
    extern void getBitsImpl_io_dma1Ext_readData_payload(svBitVecVal* value_io_dma1Ext_readData_payload);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:453:17
    extern void getBitsImpl_io_dma1Ext_readData_ready(svBitVecVal* value_io_dma1Ext_readData_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:470:17
    extern void getBitsImpl_io_dma1Ext_readData_valid(svBitVecVal* value_io_dma1Ext_readData_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:359:17
    extern void getBitsImpl_io_dma1Ext_req(svBitVecVal* value_io_dma1Ext_req);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:395:17
    extern void getBitsImpl_io_dma1Ext_writeData_payload(svBitVecVal* value_io_dma1Ext_writeData_payload);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:412:17
    extern void getBitsImpl_io_dma1Ext_writeData_ready(svBitVecVal* value_io_dma1Ext_writeData_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:424:17
    extern void getBitsImpl_io_dma1Ext_writeData_valid(svBitVecVal* value_io_dma1Ext_writeData_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:226:17
    extern void getBitsImpl_io_dma2Ext_cmd_payload_addr(svBitVecVal* value_io_dma2Ext_cmd_payload_addr);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:243:17
    extern void getBitsImpl_io_dma2Ext_cmd_ready(svBitVecVal* value_io_dma2Ext_cmd_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:255:17
    extern void getBitsImpl_io_dma2Ext_cmd_valid(svBitVecVal* value_io_dma2Ext_cmd_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:289:17
    extern void getBitsImpl_io_dma2Ext_rsp_payload_data(svBitVecVal* value_io_dma2Ext_rsp_payload_data);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:272:17
    extern void getBitsImpl_io_dma2Ext_rsp_payload_rsp(svBitVecVal* value_io_dma2Ext_rsp_payload_rsp);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:301:17
    extern void getBitsImpl_io_dma2Ext_rsp_ready(svBitVecVal* value_io_dma2Ext_rsp_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:318:17
    extern void getBitsImpl_io_dma2Ext_rsp_valid(svBitVecVal* value_io_dma2Ext_rsp_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:610:17
    extern void getBitsImpl_io_resp_payload_data(svBitVecVal* value_io_resp_payload_data);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:598:17
    extern void getBitsImpl_io_resp_payload_done(svBitVecVal* value_io_resp_payload_done);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:586:17
    extern void getBitsImpl_io_resp_payload_err(svBitVecVal* value_io_resp_payload_err);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:627:17
    extern void getBitsImpl_io_resp_ready(svBitVecVal* value_io_resp_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:639:17
    extern void getBitsImpl_io_resp_valid(svBitVecVal* value_io_resp_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:166:17
    extern void getBitsImpl_io_status_aOccupancy(svBitVecVal* value_io_status_aOccupancy);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:154:17
    extern void getBitsImpl_io_status_bOccupancy(svBitVecVal* value_io_status_bOccupancy);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:214:17
    extern void getBitsImpl_io_status_busy(svBitVecVal* value_io_status_busy);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:142:17
    extern void getBitsImpl_io_status_cOccupancy(svBitVecVal* value_io_status_cOccupancy);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:202:17
    extern void getBitsImpl_io_status_done(svBitVecVal* value_io_status_done);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:190:17
    extern void getBitsImpl_io_status_err(svBitVecVal* value_io_status_err);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:178:17
    extern void getBitsImpl_io_status_progress(svBitVecVal* value_io_status_progress);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:130:17
    extern void getBitsImpl_reset(svBitVecVal* value_reset);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:108:17
    extern void setBitsImpl_clock(const svBitVecVal* value_clock);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:668:17
    extern void setBitsImpl_io_cmd_payload_data0(const svBitVecVal* value_io_cmd_payload_data0);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:651:17
    extern void setBitsImpl_io_cmd_payload_data1(const svBitVecVal* value_io_cmd_payload_data1);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:719:17
    extern void setBitsImpl_io_cmd_payload_op(const svBitVecVal* value_io_cmd_payload_op);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:702:17
    extern void setBitsImpl_io_cmd_payload_reg0Addr(const svBitVecVal* value_io_cmd_payload_reg0Addr);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:685:17
    extern void setBitsImpl_io_cmd_payload_reg1Addr(const svBitVecVal* value_io_cmd_payload_reg1Addr);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:748:17
    extern void setBitsImpl_io_cmd_valid(const svBitVecVal* value_io_cmd_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:494:17
    extern void setBitsImpl_io_dma0Ext_cmd_ready(const svBitVecVal* value_io_dma0Ext_cmd_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:540:17
    extern void setBitsImpl_io_dma0Ext_rsp_payload_data(const svBitVecVal* value_io_dma0Ext_rsp_payload_data);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:523:17
    extern void setBitsImpl_io_dma0Ext_rsp_payload_rsp(const svBitVecVal* value_io_dma0Ext_rsp_payload_rsp);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:569:17
    extern void setBitsImpl_io_dma0Ext_rsp_valid(const svBitVecVal* value_io_dma0Ext_rsp_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:342:17
    extern void setBitsImpl_io_dma1Ext_grant(const svBitVecVal* value_io_dma1Ext_grant);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:436:17
    extern void setBitsImpl_io_dma1Ext_readData_payload(const svBitVecVal* value_io_dma1Ext_readData_payload);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:465:17
    extern void setBitsImpl_io_dma1Ext_readData_valid(const svBitVecVal* value_io_dma1Ext_readData_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:407:17
    extern void setBitsImpl_io_dma1Ext_writeData_ready(const svBitVecVal* value_io_dma1Ext_writeData_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:238:17
    extern void setBitsImpl_io_dma2Ext_cmd_ready(const svBitVecVal* value_io_dma2Ext_cmd_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:284:17
    extern void setBitsImpl_io_dma2Ext_rsp_payload_data(const svBitVecVal* value_io_dma2Ext_rsp_payload_data);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:267:17
    extern void setBitsImpl_io_dma2Ext_rsp_payload_rsp(const svBitVecVal* value_io_dma2Ext_rsp_payload_rsp);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:313:17
    extern void setBitsImpl_io_dma2Ext_rsp_valid(const svBitVecVal* value_io_dma2Ext_rsp_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:622:17
    extern void setBitsImpl_io_resp_ready(const svBitVecVal* value_io_resp_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:125:17
    extern void setBitsImpl_reset(const svBitVecVal* value_reset);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:826:17
    extern void simulation_disableTrace(int* success);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:811:17
    extern void simulation_enableTrace(int* success);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:787:17
    extern void simulation_initializeTrace(const char* traceFilePath);

    // DPI IMPORTS
    // DPI import at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:98:40
    extern void initTestBenchScope();
    // DPI import at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:781:32
    extern void run_simulation(int timesteps, int* done);
    // DPI import at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:759:31
    extern int simulation_body();
    // DPI import at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreMultiPassSpec/HiveCore-multi-pass-K-split-GEMM--partial-sum-feedback-/should-handle-multi-N-tiles-x-multi-K-passes--M-16-N-32-K-32-in-FP16/workdir-verilator/../generated-sources/testbench.sv:769:31
    extern int simulation_final();

#ifdef __cplusplus
}
#endif

#endif  // guard
