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
void getBitWidth_io_rndOut(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_rndOut(result);
           svSetScope(prev);
        }
void getBits_io_rndOut(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_rndOut(result);
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
void getBitWidth_io_fmtOut(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_fmtOut(result);
           svSetScope(prev);
        }
void getBits_io_fmtOut(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_fmtOut(result);
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
void getBitWidth_io_validOut(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_validOut(result);
           svSetScope(prev);
        }
void getBits_io_validOut(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_validOut(result);
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
void getBitWidth_io_psumOut(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_psumOut(result);
           svSetScope(prev);
        }
void getBits_io_psumOut(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_psumOut(result);
           svSetScope(prev);
        }
void getBitWidth_io_psumIn(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_psumIn(result);
           svSetScope(prev);
        }
void getBits_io_psumIn(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_psumIn(result);
           svSetScope(prev);
        }
void setBits_io_psumIn(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_psumIn(data);
           svSetScope(prev);
        }
void getBitWidth_io_aOut(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_aOut(result);
           svSetScope(prev);
        }
void getBits_io_aOut(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_aOut(result);
           svSetScope(prev);
        }
void getBitWidth_io_aIn(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_aIn(result);
           svSetScope(prev);
        }
void getBits_io_aIn(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_aIn(result);
           svSetScope(prev);
        }
void setBits_io_aIn(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_aIn(data);
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
    case 2: // io_clear
      getBitWidth_io_clear(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_clear;
      return 0;
    case 3: // io_rndOut
      getBitWidth_io_rndOut(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_rndOut;
      return 0;
    case 4: // io_rndIn
      getBitWidth_io_rndIn(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_rndIn;
      return 0;
    case 5: // io_fmtOut
      getBitWidth_io_fmtOut(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_fmtOut;
      return 0;
    case 6: // io_fmtIn
      getBitWidth_io_fmtIn(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_fmtIn;
      return 0;
    case 7: // io_validOut
      getBitWidth_io_validOut(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_validOut;
      return 0;
    case 8: // io_validIn
      getBitWidth_io_validIn(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_validIn;
      return 0;
    case 9: // io_loadVIn
      getBitWidth_io_loadVIn(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_loadVIn;
      return 0;
    case 10: // io_loadVLock
      getBitWidth_io_loadVLock(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_loadVLock;
      return 0;
    case 11: // io_loadHIn
      getBitWidth_io_loadHIn(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_loadHIn;
      return 0;
    case 12: // io_psumOut
      getBitWidth_io_psumOut(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_psumOut;
      return 0;
    case 13: // io_psumIn
      getBitWidth_io_psumIn(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_psumIn;
      return 0;
    case 14: // io_aOut
      getBitWidth_io_aOut(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_aOut;
      return 0;
    case 15: // io_aIn
      getBitWidth_io_aIn(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_aIn;
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
    case 2: // io_clear
      getBitWidth_io_clear(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_clear;
      return 0;
    case 4: // io_rndIn
      getBitWidth_io_rndIn(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_rndIn;
      return 0;
    case 6: // io_fmtIn
      getBitWidth_io_fmtIn(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_fmtIn;
      return 0;
    case 8: // io_validIn
      getBitWidth_io_validIn(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_validIn;
      return 0;
    case 9: // io_loadVIn
      getBitWidth_io_loadVIn(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_loadVIn;
      return 0;
    case 10: // io_loadVLock
      getBitWidth_io_loadVLock(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_loadVLock;
      return 0;
    case 11: // io_loadHIn
      getBitWidth_io_loadHIn(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_loadHIn;
      return 0;
    case 13: // io_psumIn
      getBitWidth_io_psumIn(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_psumIn;
      return 0;
    case 15: // io_aIn
      getBitWidth_io_aIn(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_aIn;
      return 0;
    default:
      return -1;
  }
}

} // extern "C"

