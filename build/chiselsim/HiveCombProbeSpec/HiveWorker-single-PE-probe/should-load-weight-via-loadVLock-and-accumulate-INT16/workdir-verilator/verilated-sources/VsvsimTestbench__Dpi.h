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
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:43:17
    extern void getBitWidthImpl_clock(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:273:17
    extern void getBitWidthImpl_io_aIn(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:261:17
    extern void getBitWidthImpl_io_aOut(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:77:17
    extern void getBitWidthImpl_io_clear(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:135:17
    extern void getBitWidthImpl_io_fmtIn(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:123:17
    extern void getBitWidthImpl_io_fmtOut(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:215:17
    extern void getBitWidthImpl_io_loadHIn(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:181:17
    extern void getBitWidthImpl_io_loadVIn(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:198:17
    extern void getBitWidthImpl_io_loadVLock(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:244:17
    extern void getBitWidthImpl_io_psumIn(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:232:17
    extern void getBitWidthImpl_io_psumOut(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:106:17
    extern void getBitWidthImpl_io_rndIn(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:94:17
    extern void getBitWidthImpl_io_rndOut(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:164:17
    extern void getBitWidthImpl_io_validIn(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:152:17
    extern void getBitWidthImpl_io_validOut(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:60:17
    extern void getBitWidthImpl_reset(int* value);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:53:17
    extern void getBitsImpl_clock(svBitVecVal* value_clock);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:283:17
    extern void getBitsImpl_io_aIn(svBitVecVal* value_io_aIn);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:266:17
    extern void getBitsImpl_io_aOut(svBitVecVal* value_io_aOut);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:87:17
    extern void getBitsImpl_io_clear(svBitVecVal* value_io_clear);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:145:17
    extern void getBitsImpl_io_fmtIn(svBitVecVal* value_io_fmtIn);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:128:17
    extern void getBitsImpl_io_fmtOut(svBitVecVal* value_io_fmtOut);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:225:17
    extern void getBitsImpl_io_loadHIn(svBitVecVal* value_io_loadHIn);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:191:17
    extern void getBitsImpl_io_loadVIn(svBitVecVal* value_io_loadVIn);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:208:17
    extern void getBitsImpl_io_loadVLock(svBitVecVal* value_io_loadVLock);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:254:17
    extern void getBitsImpl_io_psumIn(svBitVecVal* value_io_psumIn);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:237:17
    extern void getBitsImpl_io_psumOut(svBitVecVal* value_io_psumOut);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:116:17
    extern void getBitsImpl_io_rndIn(svBitVecVal* value_io_rndIn);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:99:17
    extern void getBitsImpl_io_rndOut(svBitVecVal* value_io_rndOut);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:174:17
    extern void getBitsImpl_io_validIn(svBitVecVal* value_io_validIn);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:157:17
    extern void getBitsImpl_io_validOut(svBitVecVal* value_io_validOut);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:70:17
    extern void getBitsImpl_reset(svBitVecVal* value_reset);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:48:17
    extern void setBitsImpl_clock(const svBitVecVal* value_clock);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:278:17
    extern void setBitsImpl_io_aIn(const svBitVecVal* value_io_aIn);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:82:17
    extern void setBitsImpl_io_clear(const svBitVecVal* value_io_clear);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:140:17
    extern void setBitsImpl_io_fmtIn(const svBitVecVal* value_io_fmtIn);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:220:17
    extern void setBitsImpl_io_loadHIn(const svBitVecVal* value_io_loadHIn);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:186:17
    extern void setBitsImpl_io_loadVIn(const svBitVecVal* value_io_loadVIn);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:203:17
    extern void setBitsImpl_io_loadVLock(const svBitVecVal* value_io_loadVLock);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:249:17
    extern void setBitsImpl_io_psumIn(const svBitVecVal* value_io_psumIn);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:111:17
    extern void setBitsImpl_io_rndIn(const svBitVecVal* value_io_rndIn);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:169:17
    extern void setBitsImpl_io_validIn(const svBitVecVal* value_io_validIn);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:65:17
    extern void setBitsImpl_reset(const svBitVecVal* value_reset);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:356:17
    extern void simulation_disableTrace(int* success);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:341:17
    extern void simulation_enableTrace(int* success);
    // DPI export at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:317:17
    extern void simulation_initializeTrace(const char* traceFilePath);

    // DPI IMPORTS
    // DPI import at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:38:40
    extern void initTestBenchScope();
    // DPI import at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:311:32
    extern void run_simulation(int timesteps, int* done);
    // DPI import at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:289:31
    extern int simulation_body();
    // DPI import at /Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv:299:31
    extern int simulation_final();

#ifdef __cplusplus
}
#endif

#endif  // guard
