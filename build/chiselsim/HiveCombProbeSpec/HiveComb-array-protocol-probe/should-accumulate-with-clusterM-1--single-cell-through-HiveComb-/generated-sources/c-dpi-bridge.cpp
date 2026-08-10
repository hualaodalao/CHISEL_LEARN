#include <stdint.h>

#ifdef SVSIM_ENABLE_VERILATOR_SUPPORT
#include "verilated-sources/VsvsimTestbench__Dpi.h"
#endif
#ifdef SVSIM_ENABLE_VCS_SUPPORT
#include "vc_hdrs.h"
#endif

extern "C" {
 svScope setScopeToTestBench();
void getBitWidth_clock(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_clock(result);
           svSetScope(prev);
        }
void getBits_clock(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_clock(result);
           svSetScope(prev);
        }
void setBits_clock(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_clock(data);
           svSetScope(prev);
        }
void getBitWidth_reset(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_reset(result);
           svSetScope(prev);
        }
void getBits_reset(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_reset(result);
           svSetScope(prev);
        }
void setBits_reset(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_reset(data);
           svSetScope(prev);
        }
void getBitWidth_io_validOut_0(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_validOut_0(result);
           svSetScope(prev);
        }
void getBits_io_validOut_0(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_validOut_0(result);
           svSetScope(prev);
        }
void getBitWidth_io_validOut_1(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_validOut_1(result);
           svSetScope(prev);
        }
void getBits_io_validOut_1(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_validOut_1(result);
           svSetScope(prev);
        }
void getBitWidth_io_validOut_2(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_validOut_2(result);
           svSetScope(prev);
        }
void getBits_io_validOut_2(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_validOut_2(result);
           svSetScope(prev);
        }
void getBitWidth_io_validOut_3(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_validOut_3(result);
           svSetScope(prev);
        }
void getBits_io_validOut_3(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_validOut_3(result);
           svSetScope(prev);
        }
void getBitWidth_io_validOut_4(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_validOut_4(result);
           svSetScope(prev);
        }
void getBits_io_validOut_4(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_validOut_4(result);
           svSetScope(prev);
        }
void getBitWidth_io_validOut_5(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_validOut_5(result);
           svSetScope(prev);
        }
void getBits_io_validOut_5(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_validOut_5(result);
           svSetScope(prev);
        }
void getBitWidth_io_validOut_6(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_validOut_6(result);
           svSetScope(prev);
        }
void getBits_io_validOut_6(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_validOut_6(result);
           svSetScope(prev);
        }
void getBitWidth_io_validOut_7(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_validOut_7(result);
           svSetScope(prev);
        }
void getBits_io_validOut_7(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_validOut_7(result);
           svSetScope(prev);
        }
void getBitWidth_io_loadVLock(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_loadVLock(result);
           svSetScope(prev);
        }
void getBits_io_loadVLock(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_loadVLock(result);
           svSetScope(prev);
        }
void setBits_io_loadVLock(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_loadVLock(data);
           svSetScope(prev);
        }
void getBitWidth_io_clear(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_clear(result);
           svSetScope(prev);
        }
void getBits_io_clear(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_clear(result);
           svSetScope(prev);
        }
void setBits_io_clear(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_clear(data);
           svSetScope(prev);
        }
void getBitWidth_io_rndIn(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_rndIn(result);
           svSetScope(prev);
        }
void getBits_io_rndIn(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_rndIn(result);
           svSetScope(prev);
        }
void setBits_io_rndIn(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_rndIn(data);
           svSetScope(prev);
        }
void getBitWidth_io_fmtIn(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_fmtIn(result);
           svSetScope(prev);
        }
void getBits_io_fmtIn(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_fmtIn(result);
           svSetScope(prev);
        }
void setBits_io_fmtIn(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_fmtIn(data);
           svSetScope(prev);
        }
void getBitWidth_io_validIn(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_validIn(result);
           svSetScope(prev);
        }
void getBits_io_validIn(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_validIn(result);
           svSetScope(prev);
        }
void setBits_io_validIn(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_validIn(data);
           svSetScope(prev);
        }
void getBitWidth_io_loadVIn(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_loadVIn(result);
           svSetScope(prev);
        }
void getBits_io_loadVIn(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_loadVIn(result);
           svSetScope(prev);
        }
void setBits_io_loadVIn(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_loadVIn(data);
           svSetScope(prev);
        }
void getBitWidth_io_loadHIn(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_loadHIn(result);
           svSetScope(prev);
        }
void getBits_io_loadHIn(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_loadHIn(result);
           svSetScope(prev);
        }
void setBits_io_loadHIn(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_loadHIn(data);
           svSetScope(prev);
        }
void getBitWidth_io_cOut_0(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_cOut_0(result);
           svSetScope(prev);
        }
void getBits_io_cOut_0(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_cOut_0(result);
           svSetScope(prev);
        }
void getBitWidth_io_cOut_1(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_cOut_1(result);
           svSetScope(prev);
        }
void getBits_io_cOut_1(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_cOut_1(result);
           svSetScope(prev);
        }
void getBitWidth_io_cOut_2(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_cOut_2(result);
           svSetScope(prev);
        }
void getBits_io_cOut_2(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_cOut_2(result);
           svSetScope(prev);
        }
void getBitWidth_io_cOut_3(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_cOut_3(result);
           svSetScope(prev);
        }
void getBits_io_cOut_3(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_cOut_3(result);
           svSetScope(prev);
        }
void getBitWidth_io_cOut_4(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_cOut_4(result);
           svSetScope(prev);
        }
void getBits_io_cOut_4(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_cOut_4(result);
           svSetScope(prev);
        }
void getBitWidth_io_cOut_5(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_cOut_5(result);
           svSetScope(prev);
        }
void getBits_io_cOut_5(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_cOut_5(result);
           svSetScope(prev);
        }
void getBitWidth_io_cOut_6(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_cOut_6(result);
           svSetScope(prev);
        }
void getBits_io_cOut_6(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_cOut_6(result);
           svSetScope(prev);
        }
void getBitWidth_io_cOut_7(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_cOut_7(result);
           svSetScope(prev);
        }
void getBits_io_cOut_7(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_cOut_7(result);
           svSetScope(prev);
        }
void getBitWidth_io_psumIn_0(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_psumIn_0(result);
           svSetScope(prev);
        }
void getBits_io_psumIn_0(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_psumIn_0(result);
           svSetScope(prev);
        }
void setBits_io_psumIn_0(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_psumIn_0(data);
           svSetScope(prev);
        }
void getBitWidth_io_psumIn_1(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_psumIn_1(result);
           svSetScope(prev);
        }
void getBits_io_psumIn_1(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_psumIn_1(result);
           svSetScope(prev);
        }
void setBits_io_psumIn_1(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_psumIn_1(data);
           svSetScope(prev);
        }
void getBitWidth_io_psumIn_2(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_psumIn_2(result);
           svSetScope(prev);
        }
void getBits_io_psumIn_2(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_psumIn_2(result);
           svSetScope(prev);
        }
void setBits_io_psumIn_2(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_psumIn_2(data);
           svSetScope(prev);
        }
void getBitWidth_io_psumIn_3(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_psumIn_3(result);
           svSetScope(prev);
        }
void getBits_io_psumIn_3(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_psumIn_3(result);
           svSetScope(prev);
        }
void setBits_io_psumIn_3(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_psumIn_3(data);
           svSetScope(prev);
        }
void getBitWidth_io_psumIn_4(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_psumIn_4(result);
           svSetScope(prev);
        }
void getBits_io_psumIn_4(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_psumIn_4(result);
           svSetScope(prev);
        }
void setBits_io_psumIn_4(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_psumIn_4(data);
           svSetScope(prev);
        }
void getBitWidth_io_psumIn_5(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_psumIn_5(result);
           svSetScope(prev);
        }
void getBits_io_psumIn_5(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_psumIn_5(result);
           svSetScope(prev);
        }
void setBits_io_psumIn_5(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_psumIn_5(data);
           svSetScope(prev);
        }
void getBitWidth_io_psumIn_6(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_psumIn_6(result);
           svSetScope(prev);
        }
void getBits_io_psumIn_6(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_psumIn_6(result);
           svSetScope(prev);
        }
void setBits_io_psumIn_6(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_psumIn_6(data);
           svSetScope(prev);
        }
void getBitWidth_io_psumIn_7(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_psumIn_7(result);
           svSetScope(prev);
        }
void getBits_io_psumIn_7(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_psumIn_7(result);
           svSetScope(prev);
        }
void setBits_io_psumIn_7(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_psumIn_7(data);
           svSetScope(prev);
        }
void getBitWidth_io_aOut_0(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_aOut_0(result);
           svSetScope(prev);
        }
void getBits_io_aOut_0(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_aOut_0(result);
           svSetScope(prev);
        }
void getBitWidth_io_aOut_1(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_aOut_1(result);
           svSetScope(prev);
        }
void getBits_io_aOut_1(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_aOut_1(result);
           svSetScope(prev);
        }
void getBitWidth_io_aOut_2(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_aOut_2(result);
           svSetScope(prev);
        }
void getBits_io_aOut_2(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_aOut_2(result);
           svSetScope(prev);
        }
void getBitWidth_io_aOut_3(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_aOut_3(result);
           svSetScope(prev);
        }
void getBits_io_aOut_3(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_aOut_3(result);
           svSetScope(prev);
        }
void getBitWidth_io_aOut_4(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_aOut_4(result);
           svSetScope(prev);
        }
void getBits_io_aOut_4(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_aOut_4(result);
           svSetScope(prev);
        }
void getBitWidth_io_aOut_5(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_aOut_5(result);
           svSetScope(prev);
        }
void getBits_io_aOut_5(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_aOut_5(result);
           svSetScope(prev);
        }
void getBitWidth_io_aOut_6(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_aOut_6(result);
           svSetScope(prev);
        }
void getBits_io_aOut_6(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_aOut_6(result);
           svSetScope(prev);
        }
void getBitWidth_io_aOut_7(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_aOut_7(result);
           svSetScope(prev);
        }
void getBits_io_aOut_7(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_aOut_7(result);
           svSetScope(prev);
        }
void getBitWidth_io_aIn_0(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_aIn_0(result);
           svSetScope(prev);
        }
void getBits_io_aIn_0(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_aIn_0(result);
           svSetScope(prev);
        }
void setBits_io_aIn_0(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_aIn_0(data);
           svSetScope(prev);
        }
void getBitWidth_io_aIn_1(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_aIn_1(result);
           svSetScope(prev);
        }
void getBits_io_aIn_1(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_aIn_1(result);
           svSetScope(prev);
        }
void setBits_io_aIn_1(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_aIn_1(data);
           svSetScope(prev);
        }
void getBitWidth_io_aIn_2(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_aIn_2(result);
           svSetScope(prev);
        }
void getBits_io_aIn_2(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_aIn_2(result);
           svSetScope(prev);
        }
void setBits_io_aIn_2(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_aIn_2(data);
           svSetScope(prev);
        }
void getBitWidth_io_aIn_3(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_aIn_3(result);
           svSetScope(prev);
        }
void getBits_io_aIn_3(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_aIn_3(result);
           svSetScope(prev);
        }
void setBits_io_aIn_3(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_aIn_3(data);
           svSetScope(prev);
        }
void getBitWidth_io_aIn_4(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_aIn_4(result);
           svSetScope(prev);
        }
void getBits_io_aIn_4(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_aIn_4(result);
           svSetScope(prev);
        }
void setBits_io_aIn_4(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_aIn_4(data);
           svSetScope(prev);
        }
void getBitWidth_io_aIn_5(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_aIn_5(result);
           svSetScope(prev);
        }
void getBits_io_aIn_5(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_aIn_5(result);
           svSetScope(prev);
        }
void setBits_io_aIn_5(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_aIn_5(data);
           svSetScope(prev);
        }
void getBitWidth_io_aIn_6(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_aIn_6(result);
           svSetScope(prev);
        }
void getBits_io_aIn_6(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_aIn_6(result);
           svSetScope(prev);
        }
void setBits_io_aIn_6(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_aIn_6(data);
           svSetScope(prev);
        }
void getBitWidth_io_aIn_7(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_aIn_7(result);
           svSetScope(prev);
        }
void getBits_io_aIn_7(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_aIn_7(result);
           svSetScope(prev);
        }
void setBits_io_aIn_7(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_aIn_7(data);
           svSetScope(prev);
        }

int port_getter(int id, int *bitWidth, void (**getter)(uint8_t*)) {
  switch (id) {
    case 0: // clock
      getBitWidth_clock(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_clock;
      return 0;
    case 1: // reset
      getBitWidth_reset(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_reset;
      return 0;
    case 2: // io_validOut_0
      getBitWidth_io_validOut_0(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_validOut_0;
      return 0;
    case 3: // io_validOut_1
      getBitWidth_io_validOut_1(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_validOut_1;
      return 0;
    case 4: // io_validOut_2
      getBitWidth_io_validOut_2(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_validOut_2;
      return 0;
    case 5: // io_validOut_3
      getBitWidth_io_validOut_3(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_validOut_3;
      return 0;
    case 6: // io_validOut_4
      getBitWidth_io_validOut_4(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_validOut_4;
      return 0;
    case 7: // io_validOut_5
      getBitWidth_io_validOut_5(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_validOut_5;
      return 0;
    case 8: // io_validOut_6
      getBitWidth_io_validOut_6(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_validOut_6;
      return 0;
    case 9: // io_validOut_7
      getBitWidth_io_validOut_7(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_validOut_7;
      return 0;
    case 10: // io_loadVLock
      getBitWidth_io_loadVLock(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_loadVLock;
      return 0;
    case 11: // io_clear
      getBitWidth_io_clear(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_clear;
      return 0;
    case 12: // io_rndIn
      getBitWidth_io_rndIn(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_rndIn;
      return 0;
    case 13: // io_fmtIn
      getBitWidth_io_fmtIn(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_fmtIn;
      return 0;
    case 14: // io_validIn
      getBitWidth_io_validIn(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_validIn;
      return 0;
    case 15: // io_loadVIn
      getBitWidth_io_loadVIn(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_loadVIn;
      return 0;
    case 16: // io_loadHIn
      getBitWidth_io_loadHIn(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_loadHIn;
      return 0;
    case 17: // io_cOut_0
      getBitWidth_io_cOut_0(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_cOut_0;
      return 0;
    case 18: // io_cOut_1
      getBitWidth_io_cOut_1(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_cOut_1;
      return 0;
    case 19: // io_cOut_2
      getBitWidth_io_cOut_2(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_cOut_2;
      return 0;
    case 20: // io_cOut_3
      getBitWidth_io_cOut_3(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_cOut_3;
      return 0;
    case 21: // io_cOut_4
      getBitWidth_io_cOut_4(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_cOut_4;
      return 0;
    case 22: // io_cOut_5
      getBitWidth_io_cOut_5(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_cOut_5;
      return 0;
    case 23: // io_cOut_6
      getBitWidth_io_cOut_6(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_cOut_6;
      return 0;
    case 24: // io_cOut_7
      getBitWidth_io_cOut_7(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_cOut_7;
      return 0;
    case 25: // io_psumIn_0
      getBitWidth_io_psumIn_0(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_psumIn_0;
      return 0;
    case 26: // io_psumIn_1
      getBitWidth_io_psumIn_1(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_psumIn_1;
      return 0;
    case 27: // io_psumIn_2
      getBitWidth_io_psumIn_2(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_psumIn_2;
      return 0;
    case 28: // io_psumIn_3
      getBitWidth_io_psumIn_3(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_psumIn_3;
      return 0;
    case 29: // io_psumIn_4
      getBitWidth_io_psumIn_4(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_psumIn_4;
      return 0;
    case 30: // io_psumIn_5
      getBitWidth_io_psumIn_5(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_psumIn_5;
      return 0;
    case 31: // io_psumIn_6
      getBitWidth_io_psumIn_6(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_psumIn_6;
      return 0;
    case 32: // io_psumIn_7
      getBitWidth_io_psumIn_7(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_psumIn_7;
      return 0;
    case 33: // io_aOut_0
      getBitWidth_io_aOut_0(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_aOut_0;
      return 0;
    case 34: // io_aOut_1
      getBitWidth_io_aOut_1(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_aOut_1;
      return 0;
    case 35: // io_aOut_2
      getBitWidth_io_aOut_2(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_aOut_2;
      return 0;
    case 36: // io_aOut_3
      getBitWidth_io_aOut_3(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_aOut_3;
      return 0;
    case 37: // io_aOut_4
      getBitWidth_io_aOut_4(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_aOut_4;
      return 0;
    case 38: // io_aOut_5
      getBitWidth_io_aOut_5(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_aOut_5;
      return 0;
    case 39: // io_aOut_6
      getBitWidth_io_aOut_6(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_aOut_6;
      return 0;
    case 40: // io_aOut_7
      getBitWidth_io_aOut_7(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_aOut_7;
      return 0;
    case 41: // io_aIn_0
      getBitWidth_io_aIn_0(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_aIn_0;
      return 0;
    case 42: // io_aIn_1
      getBitWidth_io_aIn_1(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_aIn_1;
      return 0;
    case 43: // io_aIn_2
      getBitWidth_io_aIn_2(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_aIn_2;
      return 0;
    case 44: // io_aIn_3
      getBitWidth_io_aIn_3(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_aIn_3;
      return 0;
    case 45: // io_aIn_4
      getBitWidth_io_aIn_4(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_aIn_4;
      return 0;
    case 46: // io_aIn_5
      getBitWidth_io_aIn_5(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_aIn_5;
      return 0;
    case 47: // io_aIn_6
      getBitWidth_io_aIn_6(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_aIn_6;
      return 0;
    case 48: // io_aIn_7
      getBitWidth_io_aIn_7(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_aIn_7;
      return 0;
    default:
      return -1;
  }
}

int port_setter(int id, int *bitWidth, void (**setter)(const uint8_t*)) {
  switch (id) {
    case 0: // clock
      getBitWidth_clock(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_clock;
      return 0;
    case 1: // reset
      getBitWidth_reset(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_reset;
      return 0;
    case 10: // io_loadVLock
      getBitWidth_io_loadVLock(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_loadVLock;
      return 0;
    case 11: // io_clear
      getBitWidth_io_clear(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_clear;
      return 0;
    case 12: // io_rndIn
      getBitWidth_io_rndIn(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_rndIn;
      return 0;
    case 13: // io_fmtIn
      getBitWidth_io_fmtIn(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_fmtIn;
      return 0;
    case 14: // io_validIn
      getBitWidth_io_validIn(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_validIn;
      return 0;
    case 15: // io_loadVIn
      getBitWidth_io_loadVIn(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_loadVIn;
      return 0;
    case 16: // io_loadHIn
      getBitWidth_io_loadHIn(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_loadHIn;
      return 0;
    case 25: // io_psumIn_0
      getBitWidth_io_psumIn_0(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_psumIn_0;
      return 0;
    case 26: // io_psumIn_1
      getBitWidth_io_psumIn_1(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_psumIn_1;
      return 0;
    case 27: // io_psumIn_2
      getBitWidth_io_psumIn_2(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_psumIn_2;
      return 0;
    case 28: // io_psumIn_3
      getBitWidth_io_psumIn_3(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_psumIn_3;
      return 0;
    case 29: // io_psumIn_4
      getBitWidth_io_psumIn_4(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_psumIn_4;
      return 0;
    case 30: // io_psumIn_5
      getBitWidth_io_psumIn_5(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_psumIn_5;
      return 0;
    case 31: // io_psumIn_6
      getBitWidth_io_psumIn_6(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_psumIn_6;
      return 0;
    case 32: // io_psumIn_7
      getBitWidth_io_psumIn_7(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_psumIn_7;
      return 0;
    case 41: // io_aIn_0
      getBitWidth_io_aIn_0(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_aIn_0;
      return 0;
    case 42: // io_aIn_1
      getBitWidth_io_aIn_1(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_aIn_1;
      return 0;
    case 43: // io_aIn_2
      getBitWidth_io_aIn_2(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_aIn_2;
      return 0;
    case 44: // io_aIn_3
      getBitWidth_io_aIn_3(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_aIn_3;
      return 0;
    case 45: // io_aIn_4
      getBitWidth_io_aIn_4(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_aIn_4;
      return 0;
    case 46: // io_aIn_5
      getBitWidth_io_aIn_5(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_aIn_5;
      return 0;
    case 47: // io_aIn_6
      getBitWidth_io_aIn_6(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_aIn_6;
      return 0;
    case 48: // io_aIn_7
      getBitWidth_io_aIn_7(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_aIn_7;
      return 0;
    default:
      return -1;
  }
}

} // extern "C"

