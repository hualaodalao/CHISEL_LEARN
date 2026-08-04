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
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:93:17
    extern void getBitWidthImpl_clock(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:585:17
    extern void getBitWidthImpl_io_cmd_payload_data0(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:568:17
    extern void getBitWidthImpl_io_cmd_payload_data1(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:619:17
    extern void getBitWidthImpl_io_cmd_payload_op(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:602:17
    extern void getBitWidthImpl_io_cmd_payload_regAddr(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:636:17
    extern void getBitWidthImpl_io_cmd_ready(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:648:17
    extern void getBitWidthImpl_io_cmd_valid(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:404:17
    extern void getBitWidthImpl_io_dma0Ext_addr(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:363:17
    extern void getBitWidthImpl_io_dma0Ext_grant(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:351:17
    extern void getBitWidthImpl_io_dma0Ext_isWrite(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:392:17
    extern void getBitWidthImpl_io_dma0Ext_len(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:457:17
    extern void getBitWidthImpl_io_dma0Ext_readData_payload(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:474:17
    extern void getBitWidthImpl_io_dma0Ext_readData_ready(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:486:17
    extern void getBitWidthImpl_io_dma0Ext_readData_valid(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:380:17
    extern void getBitWidthImpl_io_dma0Ext_req(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:416:17
    extern void getBitWidthImpl_io_dma0Ext_writeData_payload(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:428:17
    extern void getBitWidthImpl_io_dma0Ext_writeData_ready(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:445:17
    extern void getBitWidthImpl_io_dma0Ext_writeData_valid(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:252:17
    extern void getBitWidthImpl_io_dma1Ext_addr(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:211:17
    extern void getBitWidthImpl_io_dma1Ext_grant(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:199:17
    extern void getBitWidthImpl_io_dma1Ext_isWrite(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:240:17
    extern void getBitWidthImpl_io_dma1Ext_len(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:305:17
    extern void getBitWidthImpl_io_dma1Ext_readData_payload(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:322:17
    extern void getBitWidthImpl_io_dma1Ext_readData_ready(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:334:17
    extern void getBitWidthImpl_io_dma1Ext_readData_valid(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:228:17
    extern void getBitWidthImpl_io_dma1Ext_req(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:264:17
    extern void getBitWidthImpl_io_dma1Ext_writeData_payload(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:276:17
    extern void getBitWidthImpl_io_dma1Ext_writeData_ready(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:293:17
    extern void getBitWidthImpl_io_dma1Ext_writeData_valid(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:527:17
    extern void getBitWidthImpl_io_resp_payload_data(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:515:17
    extern void getBitWidthImpl_io_resp_payload_done(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:503:17
    extern void getBitWidthImpl_io_resp_payload_err(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:539:17
    extern void getBitWidthImpl_io_resp_ready(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:556:17
    extern void getBitWidthImpl_io_resp_valid(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:139:17
    extern void getBitWidthImpl_io_status_aOccupancy(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:187:17
    extern void getBitWidthImpl_io_status_busy(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:127:17
    extern void getBitWidthImpl_io_status_cOccupancy(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:175:17
    extern void getBitWidthImpl_io_status_done(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:163:17
    extern void getBitWidthImpl_io_status_err(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:151:17
    extern void getBitWidthImpl_io_status_progress(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:110:17
    extern void getBitWidthImpl_reset(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:103:17
    extern void getBitsImpl_clock(svBitVecVal* value_clock);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:595:17
    extern void getBitsImpl_io_cmd_payload_data0(svBitVecVal* value_io_cmd_payload_data0);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:578:17
    extern void getBitsImpl_io_cmd_payload_data1(svBitVecVal* value_io_cmd_payload_data1);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:629:17
    extern void getBitsImpl_io_cmd_payload_op(svBitVecVal* value_io_cmd_payload_op);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:612:17
    extern void getBitsImpl_io_cmd_payload_regAddr(svBitVecVal* value_io_cmd_payload_regAddr);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:641:17
    extern void getBitsImpl_io_cmd_ready(svBitVecVal* value_io_cmd_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:658:17
    extern void getBitsImpl_io_cmd_valid(svBitVecVal* value_io_cmd_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:409:17
    extern void getBitsImpl_io_dma0Ext_addr(svBitVecVal* value_io_dma0Ext_addr);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:373:17
    extern void getBitsImpl_io_dma0Ext_grant(svBitVecVal* value_io_dma0Ext_grant);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:356:17
    extern void getBitsImpl_io_dma0Ext_isWrite(svBitVecVal* value_io_dma0Ext_isWrite);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:397:17
    extern void getBitsImpl_io_dma0Ext_len(svBitVecVal* value_io_dma0Ext_len);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:467:17
    extern void getBitsImpl_io_dma0Ext_readData_payload(svBitVecVal* value_io_dma0Ext_readData_payload);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:479:17
    extern void getBitsImpl_io_dma0Ext_readData_ready(svBitVecVal* value_io_dma0Ext_readData_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:496:17
    extern void getBitsImpl_io_dma0Ext_readData_valid(svBitVecVal* value_io_dma0Ext_readData_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:385:17
    extern void getBitsImpl_io_dma0Ext_req(svBitVecVal* value_io_dma0Ext_req);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:421:17
    extern void getBitsImpl_io_dma0Ext_writeData_payload(svBitVecVal* value_io_dma0Ext_writeData_payload);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:438:17
    extern void getBitsImpl_io_dma0Ext_writeData_ready(svBitVecVal* value_io_dma0Ext_writeData_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:450:17
    extern void getBitsImpl_io_dma0Ext_writeData_valid(svBitVecVal* value_io_dma0Ext_writeData_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:257:17
    extern void getBitsImpl_io_dma1Ext_addr(svBitVecVal* value_io_dma1Ext_addr);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:221:17
    extern void getBitsImpl_io_dma1Ext_grant(svBitVecVal* value_io_dma1Ext_grant);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:204:17
    extern void getBitsImpl_io_dma1Ext_isWrite(svBitVecVal* value_io_dma1Ext_isWrite);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:245:17
    extern void getBitsImpl_io_dma1Ext_len(svBitVecVal* value_io_dma1Ext_len);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:315:17
    extern void getBitsImpl_io_dma1Ext_readData_payload(svBitVecVal* value_io_dma1Ext_readData_payload);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:327:17
    extern void getBitsImpl_io_dma1Ext_readData_ready(svBitVecVal* value_io_dma1Ext_readData_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:344:17
    extern void getBitsImpl_io_dma1Ext_readData_valid(svBitVecVal* value_io_dma1Ext_readData_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:233:17
    extern void getBitsImpl_io_dma1Ext_req(svBitVecVal* value_io_dma1Ext_req);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:269:17
    extern void getBitsImpl_io_dma1Ext_writeData_payload(svBitVecVal* value_io_dma1Ext_writeData_payload);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:286:17
    extern void getBitsImpl_io_dma1Ext_writeData_ready(svBitVecVal* value_io_dma1Ext_writeData_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:298:17
    extern void getBitsImpl_io_dma1Ext_writeData_valid(svBitVecVal* value_io_dma1Ext_writeData_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:532:17
    extern void getBitsImpl_io_resp_payload_data(svBitVecVal* value_io_resp_payload_data);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:520:17
    extern void getBitsImpl_io_resp_payload_done(svBitVecVal* value_io_resp_payload_done);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:508:17
    extern void getBitsImpl_io_resp_payload_err(svBitVecVal* value_io_resp_payload_err);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:549:17
    extern void getBitsImpl_io_resp_ready(svBitVecVal* value_io_resp_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:561:17
    extern void getBitsImpl_io_resp_valid(svBitVecVal* value_io_resp_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:144:17
    extern void getBitsImpl_io_status_aOccupancy(svBitVecVal* value_io_status_aOccupancy);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:192:17
    extern void getBitsImpl_io_status_busy(svBitVecVal* value_io_status_busy);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:132:17
    extern void getBitsImpl_io_status_cOccupancy(svBitVecVal* value_io_status_cOccupancy);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:180:17
    extern void getBitsImpl_io_status_done(svBitVecVal* value_io_status_done);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:168:17
    extern void getBitsImpl_io_status_err(svBitVecVal* value_io_status_err);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:156:17
    extern void getBitsImpl_io_status_progress(svBitVecVal* value_io_status_progress);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:120:17
    extern void getBitsImpl_reset(svBitVecVal* value_reset);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:98:17
    extern void setBitsImpl_clock(const svBitVecVal* value_clock);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:590:17
    extern void setBitsImpl_io_cmd_payload_data0(const svBitVecVal* value_io_cmd_payload_data0);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:573:17
    extern void setBitsImpl_io_cmd_payload_data1(const svBitVecVal* value_io_cmd_payload_data1);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:624:17
    extern void setBitsImpl_io_cmd_payload_op(const svBitVecVal* value_io_cmd_payload_op);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:607:17
    extern void setBitsImpl_io_cmd_payload_regAddr(const svBitVecVal* value_io_cmd_payload_regAddr);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:653:17
    extern void setBitsImpl_io_cmd_valid(const svBitVecVal* value_io_cmd_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:368:17
    extern void setBitsImpl_io_dma0Ext_grant(const svBitVecVal* value_io_dma0Ext_grant);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:462:17
    extern void setBitsImpl_io_dma0Ext_readData_payload(const svBitVecVal* value_io_dma0Ext_readData_payload);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:491:17
    extern void setBitsImpl_io_dma0Ext_readData_valid(const svBitVecVal* value_io_dma0Ext_readData_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:433:17
    extern void setBitsImpl_io_dma0Ext_writeData_ready(const svBitVecVal* value_io_dma0Ext_writeData_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:216:17
    extern void setBitsImpl_io_dma1Ext_grant(const svBitVecVal* value_io_dma1Ext_grant);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:310:17
    extern void setBitsImpl_io_dma1Ext_readData_payload(const svBitVecVal* value_io_dma1Ext_readData_payload);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:339:17
    extern void setBitsImpl_io_dma1Ext_readData_valid(const svBitVecVal* value_io_dma1Ext_readData_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:281:17
    extern void setBitsImpl_io_dma1Ext_writeData_ready(const svBitVecVal* value_io_dma1Ext_writeData_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:544:17
    extern void setBitsImpl_io_resp_ready(const svBitVecVal* value_io_resp_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:115:17
    extern void setBitsImpl_reset(const svBitVecVal* value_reset);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:731:17
    extern void simulation_disableTrace(int* success);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:716:17
    extern void simulation_enableTrace(int* success);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:692:17
    extern void simulation_initializeTrace(const char* traceFilePath);

    // DPI IMPORTS
    // DPI import at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:88:40
    extern void initTestBenchScope();
    // DPI import at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:686:32
    extern void run_simulation(int timesteps, int* done);
    // DPI import at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:664:31
    extern int simulation_body();
    // DPI import at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/../generated-sources/testbench.sv:674:31
    extern int simulation_final();

#ifdef __cplusplus
}
#endif

#endif  // guard
