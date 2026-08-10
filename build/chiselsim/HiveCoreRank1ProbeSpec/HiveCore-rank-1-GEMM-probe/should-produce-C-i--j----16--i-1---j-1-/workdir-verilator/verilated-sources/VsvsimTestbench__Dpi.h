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
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:111:17
    extern void getBitWidthImpl_clock(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:719:17
    extern void getBitWidthImpl_io_cmd_payload_data0(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:702:17
    extern void getBitWidthImpl_io_cmd_payload_data1(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:770:17
    extern void getBitWidthImpl_io_cmd_payload_op(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:753:17
    extern void getBitWidthImpl_io_cmd_payload_reg0Addr(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:736:17
    extern void getBitWidthImpl_io_cmd_payload_reg1Addr(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:787:17
    extern void getBitWidthImpl_io_cmd_ready(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:799:17
    extern void getBitWidthImpl_io_cmd_valid(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:538:17
    extern void getBitWidthImpl_io_dma0Ext_addr(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:497:17
    extern void getBitWidthImpl_io_dma0Ext_grant(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:485:17
    extern void getBitWidthImpl_io_dma0Ext_isWrite(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:526:17
    extern void getBitWidthImpl_io_dma0Ext_len(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:591:17
    extern void getBitWidthImpl_io_dma0Ext_readData_payload(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:608:17
    extern void getBitWidthImpl_io_dma0Ext_readData_ready(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:620:17
    extern void getBitWidthImpl_io_dma0Ext_readData_valid(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:514:17
    extern void getBitWidthImpl_io_dma0Ext_req(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:550:17
    extern void getBitWidthImpl_io_dma0Ext_writeData_payload(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:562:17
    extern void getBitWidthImpl_io_dma0Ext_writeData_ready(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:579:17
    extern void getBitWidthImpl_io_dma0Ext_writeData_valid(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:386:17
    extern void getBitWidthImpl_io_dma1Ext_addr(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:345:17
    extern void getBitWidthImpl_io_dma1Ext_grant(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:333:17
    extern void getBitWidthImpl_io_dma1Ext_isWrite(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:374:17
    extern void getBitWidthImpl_io_dma1Ext_len(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:439:17
    extern void getBitWidthImpl_io_dma1Ext_readData_payload(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:456:17
    extern void getBitWidthImpl_io_dma1Ext_readData_ready(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:468:17
    extern void getBitWidthImpl_io_dma1Ext_readData_valid(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:362:17
    extern void getBitWidthImpl_io_dma1Ext_req(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:398:17
    extern void getBitWidthImpl_io_dma1Ext_writeData_payload(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:410:17
    extern void getBitWidthImpl_io_dma1Ext_writeData_ready(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:427:17
    extern void getBitWidthImpl_io_dma1Ext_writeData_valid(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:229:17
    extern void getBitWidthImpl_io_dma2Ext_cmd_payload_addr(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:241:17
    extern void getBitWidthImpl_io_dma2Ext_cmd_ready(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:258:17
    extern void getBitWidthImpl_io_dma2Ext_cmd_valid(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:287:17
    extern void getBitWidthImpl_io_dma2Ext_rsp_payload_data(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:270:17
    extern void getBitWidthImpl_io_dma2Ext_rsp_payload_rsp(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:304:17
    extern void getBitWidthImpl_io_dma2Ext_rsp_ready(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:316:17
    extern void getBitWidthImpl_io_dma2Ext_rsp_valid(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:661:17
    extern void getBitWidthImpl_io_resp_payload_data(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:649:17
    extern void getBitWidthImpl_io_resp_payload_done(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:637:17
    extern void getBitWidthImpl_io_resp_payload_err(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:673:17
    extern void getBitWidthImpl_io_resp_ready(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:690:17
    extern void getBitWidthImpl_io_resp_valid(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:169:17
    extern void getBitWidthImpl_io_status_aOccupancy(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:157:17
    extern void getBitWidthImpl_io_status_bOccupancy(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:217:17
    extern void getBitWidthImpl_io_status_busy(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:145:17
    extern void getBitWidthImpl_io_status_cOccupancy(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:205:17
    extern void getBitWidthImpl_io_status_done(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:193:17
    extern void getBitWidthImpl_io_status_err(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:181:17
    extern void getBitWidthImpl_io_status_progress(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:128:17
    extern void getBitWidthImpl_reset(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:121:17
    extern void getBitsImpl_clock(svBitVecVal* value_clock);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:729:17
    extern void getBitsImpl_io_cmd_payload_data0(svBitVecVal* value_io_cmd_payload_data0);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:712:17
    extern void getBitsImpl_io_cmd_payload_data1(svBitVecVal* value_io_cmd_payload_data1);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:780:17
    extern void getBitsImpl_io_cmd_payload_op(svBitVecVal* value_io_cmd_payload_op);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:763:17
    extern void getBitsImpl_io_cmd_payload_reg0Addr(svBitVecVal* value_io_cmd_payload_reg0Addr);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:746:17
    extern void getBitsImpl_io_cmd_payload_reg1Addr(svBitVecVal* value_io_cmd_payload_reg1Addr);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:792:17
    extern void getBitsImpl_io_cmd_ready(svBitVecVal* value_io_cmd_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:809:17
    extern void getBitsImpl_io_cmd_valid(svBitVecVal* value_io_cmd_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:543:17
    extern void getBitsImpl_io_dma0Ext_addr(svBitVecVal* value_io_dma0Ext_addr);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:507:17
    extern void getBitsImpl_io_dma0Ext_grant(svBitVecVal* value_io_dma0Ext_grant);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:490:17
    extern void getBitsImpl_io_dma0Ext_isWrite(svBitVecVal* value_io_dma0Ext_isWrite);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:531:17
    extern void getBitsImpl_io_dma0Ext_len(svBitVecVal* value_io_dma0Ext_len);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:601:17
    extern void getBitsImpl_io_dma0Ext_readData_payload(svBitVecVal* value_io_dma0Ext_readData_payload);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:613:17
    extern void getBitsImpl_io_dma0Ext_readData_ready(svBitVecVal* value_io_dma0Ext_readData_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:630:17
    extern void getBitsImpl_io_dma0Ext_readData_valid(svBitVecVal* value_io_dma0Ext_readData_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:519:17
    extern void getBitsImpl_io_dma0Ext_req(svBitVecVal* value_io_dma0Ext_req);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:555:17
    extern void getBitsImpl_io_dma0Ext_writeData_payload(svBitVecVal* value_io_dma0Ext_writeData_payload);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:572:17
    extern void getBitsImpl_io_dma0Ext_writeData_ready(svBitVecVal* value_io_dma0Ext_writeData_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:584:17
    extern void getBitsImpl_io_dma0Ext_writeData_valid(svBitVecVal* value_io_dma0Ext_writeData_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:391:17
    extern void getBitsImpl_io_dma1Ext_addr(svBitVecVal* value_io_dma1Ext_addr);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:355:17
    extern void getBitsImpl_io_dma1Ext_grant(svBitVecVal* value_io_dma1Ext_grant);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:338:17
    extern void getBitsImpl_io_dma1Ext_isWrite(svBitVecVal* value_io_dma1Ext_isWrite);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:379:17
    extern void getBitsImpl_io_dma1Ext_len(svBitVecVal* value_io_dma1Ext_len);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:449:17
    extern void getBitsImpl_io_dma1Ext_readData_payload(svBitVecVal* value_io_dma1Ext_readData_payload);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:461:17
    extern void getBitsImpl_io_dma1Ext_readData_ready(svBitVecVal* value_io_dma1Ext_readData_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:478:17
    extern void getBitsImpl_io_dma1Ext_readData_valid(svBitVecVal* value_io_dma1Ext_readData_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:367:17
    extern void getBitsImpl_io_dma1Ext_req(svBitVecVal* value_io_dma1Ext_req);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:403:17
    extern void getBitsImpl_io_dma1Ext_writeData_payload(svBitVecVal* value_io_dma1Ext_writeData_payload);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:420:17
    extern void getBitsImpl_io_dma1Ext_writeData_ready(svBitVecVal* value_io_dma1Ext_writeData_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:432:17
    extern void getBitsImpl_io_dma1Ext_writeData_valid(svBitVecVal* value_io_dma1Ext_writeData_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:234:17
    extern void getBitsImpl_io_dma2Ext_cmd_payload_addr(svBitVecVal* value_io_dma2Ext_cmd_payload_addr);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:251:17
    extern void getBitsImpl_io_dma2Ext_cmd_ready(svBitVecVal* value_io_dma2Ext_cmd_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:263:17
    extern void getBitsImpl_io_dma2Ext_cmd_valid(svBitVecVal* value_io_dma2Ext_cmd_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:297:17
    extern void getBitsImpl_io_dma2Ext_rsp_payload_data(svBitVecVal* value_io_dma2Ext_rsp_payload_data);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:280:17
    extern void getBitsImpl_io_dma2Ext_rsp_payload_rsp(svBitVecVal* value_io_dma2Ext_rsp_payload_rsp);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:309:17
    extern void getBitsImpl_io_dma2Ext_rsp_ready(svBitVecVal* value_io_dma2Ext_rsp_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:326:17
    extern void getBitsImpl_io_dma2Ext_rsp_valid(svBitVecVal* value_io_dma2Ext_rsp_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:666:17
    extern void getBitsImpl_io_resp_payload_data(svBitVecVal* value_io_resp_payload_data);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:654:17
    extern void getBitsImpl_io_resp_payload_done(svBitVecVal* value_io_resp_payload_done);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:642:17
    extern void getBitsImpl_io_resp_payload_err(svBitVecVal* value_io_resp_payload_err);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:683:17
    extern void getBitsImpl_io_resp_ready(svBitVecVal* value_io_resp_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:695:17
    extern void getBitsImpl_io_resp_valid(svBitVecVal* value_io_resp_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:174:17
    extern void getBitsImpl_io_status_aOccupancy(svBitVecVal* value_io_status_aOccupancy);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:162:17
    extern void getBitsImpl_io_status_bOccupancy(svBitVecVal* value_io_status_bOccupancy);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:222:17
    extern void getBitsImpl_io_status_busy(svBitVecVal* value_io_status_busy);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:150:17
    extern void getBitsImpl_io_status_cOccupancy(svBitVecVal* value_io_status_cOccupancy);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:210:17
    extern void getBitsImpl_io_status_done(svBitVecVal* value_io_status_done);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:198:17
    extern void getBitsImpl_io_status_err(svBitVecVal* value_io_status_err);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:186:17
    extern void getBitsImpl_io_status_progress(svBitVecVal* value_io_status_progress);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:138:17
    extern void getBitsImpl_reset(svBitVecVal* value_reset);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:116:17
    extern void setBitsImpl_clock(const svBitVecVal* value_clock);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:724:17
    extern void setBitsImpl_io_cmd_payload_data0(const svBitVecVal* value_io_cmd_payload_data0);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:707:17
    extern void setBitsImpl_io_cmd_payload_data1(const svBitVecVal* value_io_cmd_payload_data1);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:775:17
    extern void setBitsImpl_io_cmd_payload_op(const svBitVecVal* value_io_cmd_payload_op);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:758:17
    extern void setBitsImpl_io_cmd_payload_reg0Addr(const svBitVecVal* value_io_cmd_payload_reg0Addr);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:741:17
    extern void setBitsImpl_io_cmd_payload_reg1Addr(const svBitVecVal* value_io_cmd_payload_reg1Addr);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:804:17
    extern void setBitsImpl_io_cmd_valid(const svBitVecVal* value_io_cmd_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:502:17
    extern void setBitsImpl_io_dma0Ext_grant(const svBitVecVal* value_io_dma0Ext_grant);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:596:17
    extern void setBitsImpl_io_dma0Ext_readData_payload(const svBitVecVal* value_io_dma0Ext_readData_payload);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:625:17
    extern void setBitsImpl_io_dma0Ext_readData_valid(const svBitVecVal* value_io_dma0Ext_readData_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:567:17
    extern void setBitsImpl_io_dma0Ext_writeData_ready(const svBitVecVal* value_io_dma0Ext_writeData_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:350:17
    extern void setBitsImpl_io_dma1Ext_grant(const svBitVecVal* value_io_dma1Ext_grant);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:444:17
    extern void setBitsImpl_io_dma1Ext_readData_payload(const svBitVecVal* value_io_dma1Ext_readData_payload);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:473:17
    extern void setBitsImpl_io_dma1Ext_readData_valid(const svBitVecVal* value_io_dma1Ext_readData_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:415:17
    extern void setBitsImpl_io_dma1Ext_writeData_ready(const svBitVecVal* value_io_dma1Ext_writeData_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:246:17
    extern void setBitsImpl_io_dma2Ext_cmd_ready(const svBitVecVal* value_io_dma2Ext_cmd_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:292:17
    extern void setBitsImpl_io_dma2Ext_rsp_payload_data(const svBitVecVal* value_io_dma2Ext_rsp_payload_data);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:275:17
    extern void setBitsImpl_io_dma2Ext_rsp_payload_rsp(const svBitVecVal* value_io_dma2Ext_rsp_payload_rsp);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:321:17
    extern void setBitsImpl_io_dma2Ext_rsp_valid(const svBitVecVal* value_io_dma2Ext_rsp_valid);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:678:17
    extern void setBitsImpl_io_resp_ready(const svBitVecVal* value_io_resp_ready);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:133:17
    extern void setBitsImpl_reset(const svBitVecVal* value_reset);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:882:17
    extern void simulation_disableTrace(int* success);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:867:17
    extern void simulation_enableTrace(int* success);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:843:17
    extern void simulation_initializeTrace(const char* traceFilePath);

    // DPI IMPORTS
    // DPI import at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:106:40
    extern void initTestBenchScope();
    // DPI import at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:837:32
    extern void run_simulation(int timesteps, int* done);
    // DPI import at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:815:31
    extern int simulation_body();
    // DPI import at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCoreRank1ProbeSpec/HiveCore-rank-1-GEMM-probe/should-produce-C-i--j----16--i-1---j-1-/workdir-verilator/../generated-sources/testbench.sv:825:31
    extern int simulation_final();

#ifdef __cplusplus
}
#endif

#endif  // guard
