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
void getBitWidth_io_status_cOccupancy(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_status_cOccupancy(result);
           svSetScope(prev);
        }
void getBits_io_status_cOccupancy(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_status_cOccupancy(result);
           svSetScope(prev);
        }
void getBitWidth_io_status_bOccupancy(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_status_bOccupancy(result);
           svSetScope(prev);
        }
void getBits_io_status_bOccupancy(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_status_bOccupancy(result);
           svSetScope(prev);
        }
void getBitWidth_io_status_aOccupancy(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_status_aOccupancy(result);
           svSetScope(prev);
        }
void getBits_io_status_aOccupancy(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_status_aOccupancy(result);
           svSetScope(prev);
        }
void getBitWidth_io_status_progress(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_status_progress(result);
           svSetScope(prev);
        }
void getBits_io_status_progress(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_status_progress(result);
           svSetScope(prev);
        }
void getBitWidth_io_status_err(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_status_err(result);
           svSetScope(prev);
        }
void getBits_io_status_err(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_status_err(result);
           svSetScope(prev);
        }
void getBitWidth_io_status_done(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_status_done(result);
           svSetScope(prev);
        }
void getBits_io_status_done(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_status_done(result);
           svSetScope(prev);
        }
void getBitWidth_io_status_busy(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_status_busy(result);
           svSetScope(prev);
        }
void getBits_io_status_busy(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_status_busy(result);
           svSetScope(prev);
        }
void getBitWidth_io_dma2Ext_cmd_payload_addr(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma2Ext_cmd_payload_addr(result);
           svSetScope(prev);
        }
void getBits_io_dma2Ext_cmd_payload_addr(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma2Ext_cmd_payload_addr(result);
           svSetScope(prev);
        }
void getBitWidth_io_dma2Ext_cmd_ready(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma2Ext_cmd_ready(result);
           svSetScope(prev);
        }
void getBits_io_dma2Ext_cmd_ready(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma2Ext_cmd_ready(result);
           svSetScope(prev);
        }
void setBits_io_dma2Ext_cmd_ready(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_dma2Ext_cmd_ready(data);
           svSetScope(prev);
        }
void getBitWidth_io_dma2Ext_cmd_valid(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma2Ext_cmd_valid(result);
           svSetScope(prev);
        }
void getBits_io_dma2Ext_cmd_valid(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma2Ext_cmd_valid(result);
           svSetScope(prev);
        }
void getBitWidth_io_dma2Ext_rsp_payload_rsp(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma2Ext_rsp_payload_rsp(result);
           svSetScope(prev);
        }
void getBits_io_dma2Ext_rsp_payload_rsp(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma2Ext_rsp_payload_rsp(result);
           svSetScope(prev);
        }
void setBits_io_dma2Ext_rsp_payload_rsp(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_dma2Ext_rsp_payload_rsp(data);
           svSetScope(prev);
        }
void getBitWidth_io_dma2Ext_rsp_payload_data(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma2Ext_rsp_payload_data(result);
           svSetScope(prev);
        }
void getBits_io_dma2Ext_rsp_payload_data(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma2Ext_rsp_payload_data(result);
           svSetScope(prev);
        }
void setBits_io_dma2Ext_rsp_payload_data(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_dma2Ext_rsp_payload_data(data);
           svSetScope(prev);
        }
void getBitWidth_io_dma2Ext_rsp_ready(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma2Ext_rsp_ready(result);
           svSetScope(prev);
        }
void getBits_io_dma2Ext_rsp_ready(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma2Ext_rsp_ready(result);
           svSetScope(prev);
        }
void getBitWidth_io_dma2Ext_rsp_valid(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma2Ext_rsp_valid(result);
           svSetScope(prev);
        }
void getBits_io_dma2Ext_rsp_valid(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma2Ext_rsp_valid(result);
           svSetScope(prev);
        }
void setBits_io_dma2Ext_rsp_valid(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_dma2Ext_rsp_valid(data);
           svSetScope(prev);
        }
void getBitWidth_io_dma1Ext_isWrite(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma1Ext_isWrite(result);
           svSetScope(prev);
        }
void getBits_io_dma1Ext_isWrite(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma1Ext_isWrite(result);
           svSetScope(prev);
        }
void getBitWidth_io_dma1Ext_grant(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma1Ext_grant(result);
           svSetScope(prev);
        }
void getBits_io_dma1Ext_grant(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma1Ext_grant(result);
           svSetScope(prev);
        }
void setBits_io_dma1Ext_grant(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_dma1Ext_grant(data);
           svSetScope(prev);
        }
void getBitWidth_io_dma1Ext_req(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma1Ext_req(result);
           svSetScope(prev);
        }
void getBits_io_dma1Ext_req(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma1Ext_req(result);
           svSetScope(prev);
        }
void getBitWidth_io_dma1Ext_len(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma1Ext_len(result);
           svSetScope(prev);
        }
void getBits_io_dma1Ext_len(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma1Ext_len(result);
           svSetScope(prev);
        }
void getBitWidth_io_dma1Ext_addr(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma1Ext_addr(result);
           svSetScope(prev);
        }
void getBits_io_dma1Ext_addr(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma1Ext_addr(result);
           svSetScope(prev);
        }
void getBitWidth_io_dma1Ext_writeData_payload(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma1Ext_writeData_payload(result);
           svSetScope(prev);
        }
void getBits_io_dma1Ext_writeData_payload(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma1Ext_writeData_payload(result);
           svSetScope(prev);
        }
void getBitWidth_io_dma1Ext_writeData_ready(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma1Ext_writeData_ready(result);
           svSetScope(prev);
        }
void getBits_io_dma1Ext_writeData_ready(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma1Ext_writeData_ready(result);
           svSetScope(prev);
        }
void setBits_io_dma1Ext_writeData_ready(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_dma1Ext_writeData_ready(data);
           svSetScope(prev);
        }
void getBitWidth_io_dma1Ext_writeData_valid(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma1Ext_writeData_valid(result);
           svSetScope(prev);
        }
void getBits_io_dma1Ext_writeData_valid(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma1Ext_writeData_valid(result);
           svSetScope(prev);
        }
void getBitWidth_io_dma1Ext_readData_payload(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma1Ext_readData_payload(result);
           svSetScope(prev);
        }
void getBits_io_dma1Ext_readData_payload(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma1Ext_readData_payload(result);
           svSetScope(prev);
        }
void setBits_io_dma1Ext_readData_payload(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_dma1Ext_readData_payload(data);
           svSetScope(prev);
        }
void getBitWidth_io_dma1Ext_readData_ready(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma1Ext_readData_ready(result);
           svSetScope(prev);
        }
void getBits_io_dma1Ext_readData_ready(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma1Ext_readData_ready(result);
           svSetScope(prev);
        }
void getBitWidth_io_dma1Ext_readData_valid(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma1Ext_readData_valid(result);
           svSetScope(prev);
        }
void getBits_io_dma1Ext_readData_valid(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma1Ext_readData_valid(result);
           svSetScope(prev);
        }
void setBits_io_dma1Ext_readData_valid(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_dma1Ext_readData_valid(data);
           svSetScope(prev);
        }
void getBitWidth_io_dma0Ext_isWrite(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma0Ext_isWrite(result);
           svSetScope(prev);
        }
void getBits_io_dma0Ext_isWrite(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma0Ext_isWrite(result);
           svSetScope(prev);
        }
void getBitWidth_io_dma0Ext_grant(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma0Ext_grant(result);
           svSetScope(prev);
        }
void getBits_io_dma0Ext_grant(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma0Ext_grant(result);
           svSetScope(prev);
        }
void setBits_io_dma0Ext_grant(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_dma0Ext_grant(data);
           svSetScope(prev);
        }
void getBitWidth_io_dma0Ext_req(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma0Ext_req(result);
           svSetScope(prev);
        }
void getBits_io_dma0Ext_req(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma0Ext_req(result);
           svSetScope(prev);
        }
void getBitWidth_io_dma0Ext_len(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma0Ext_len(result);
           svSetScope(prev);
        }
void getBits_io_dma0Ext_len(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma0Ext_len(result);
           svSetScope(prev);
        }
void getBitWidth_io_dma0Ext_addr(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma0Ext_addr(result);
           svSetScope(prev);
        }
void getBits_io_dma0Ext_addr(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma0Ext_addr(result);
           svSetScope(prev);
        }
void getBitWidth_io_dma0Ext_writeData_payload(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma0Ext_writeData_payload(result);
           svSetScope(prev);
        }
void getBits_io_dma0Ext_writeData_payload(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma0Ext_writeData_payload(result);
           svSetScope(prev);
        }
void getBitWidth_io_dma0Ext_writeData_ready(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma0Ext_writeData_ready(result);
           svSetScope(prev);
        }
void getBits_io_dma0Ext_writeData_ready(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma0Ext_writeData_ready(result);
           svSetScope(prev);
        }
void setBits_io_dma0Ext_writeData_ready(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_dma0Ext_writeData_ready(data);
           svSetScope(prev);
        }
void getBitWidth_io_dma0Ext_writeData_valid(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma0Ext_writeData_valid(result);
           svSetScope(prev);
        }
void getBits_io_dma0Ext_writeData_valid(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma0Ext_writeData_valid(result);
           svSetScope(prev);
        }
void getBitWidth_io_dma0Ext_readData_payload(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma0Ext_readData_payload(result);
           svSetScope(prev);
        }
void getBits_io_dma0Ext_readData_payload(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma0Ext_readData_payload(result);
           svSetScope(prev);
        }
void setBits_io_dma0Ext_readData_payload(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_dma0Ext_readData_payload(data);
           svSetScope(prev);
        }
void getBitWidth_io_dma0Ext_readData_ready(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma0Ext_readData_ready(result);
           svSetScope(prev);
        }
void getBits_io_dma0Ext_readData_ready(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma0Ext_readData_ready(result);
           svSetScope(prev);
        }
void getBitWidth_io_dma0Ext_readData_valid(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_dma0Ext_readData_valid(result);
           svSetScope(prev);
        }
void getBits_io_dma0Ext_readData_valid(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_dma0Ext_readData_valid(result);
           svSetScope(prev);
        }
void setBits_io_dma0Ext_readData_valid(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_dma0Ext_readData_valid(data);
           svSetScope(prev);
        }
void getBitWidth_io_resp_payload_err(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_resp_payload_err(result);
           svSetScope(prev);
        }
void getBits_io_resp_payload_err(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_resp_payload_err(result);
           svSetScope(prev);
        }
void getBitWidth_io_resp_payload_done(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_resp_payload_done(result);
           svSetScope(prev);
        }
void getBits_io_resp_payload_done(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_resp_payload_done(result);
           svSetScope(prev);
        }
void getBitWidth_io_resp_payload_data(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_resp_payload_data(result);
           svSetScope(prev);
        }
void getBits_io_resp_payload_data(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_resp_payload_data(result);
           svSetScope(prev);
        }
void getBitWidth_io_resp_ready(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_resp_ready(result);
           svSetScope(prev);
        }
void getBits_io_resp_ready(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_resp_ready(result);
           svSetScope(prev);
        }
void setBits_io_resp_ready(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_resp_ready(data);
           svSetScope(prev);
        }
void getBitWidth_io_resp_valid(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_resp_valid(result);
           svSetScope(prev);
        }
void getBits_io_resp_valid(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_resp_valid(result);
           svSetScope(prev);
        }
void getBitWidth_io_cmd_payload_data1(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_cmd_payload_data1(result);
           svSetScope(prev);
        }
void getBits_io_cmd_payload_data1(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_cmd_payload_data1(result);
           svSetScope(prev);
        }
void setBits_io_cmd_payload_data1(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_cmd_payload_data1(data);
           svSetScope(prev);
        }
void getBitWidth_io_cmd_payload_data0(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_cmd_payload_data0(result);
           svSetScope(prev);
        }
void getBits_io_cmd_payload_data0(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_cmd_payload_data0(result);
           svSetScope(prev);
        }
void setBits_io_cmd_payload_data0(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_cmd_payload_data0(data);
           svSetScope(prev);
        }
void getBitWidth_io_cmd_payload_reg1Addr(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_cmd_payload_reg1Addr(result);
           svSetScope(prev);
        }
void getBits_io_cmd_payload_reg1Addr(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_cmd_payload_reg1Addr(result);
           svSetScope(prev);
        }
void setBits_io_cmd_payload_reg1Addr(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_cmd_payload_reg1Addr(data);
           svSetScope(prev);
        }
void getBitWidth_io_cmd_payload_reg0Addr(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_cmd_payload_reg0Addr(result);
           svSetScope(prev);
        }
void getBits_io_cmd_payload_reg0Addr(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_cmd_payload_reg0Addr(result);
           svSetScope(prev);
        }
void setBits_io_cmd_payload_reg0Addr(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_cmd_payload_reg0Addr(data);
           svSetScope(prev);
        }
void getBitWidth_io_cmd_payload_op(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_cmd_payload_op(result);
           svSetScope(prev);
        }
void getBits_io_cmd_payload_op(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_cmd_payload_op(result);
           svSetScope(prev);
        }
void setBits_io_cmd_payload_op(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_cmd_payload_op(data);
           svSetScope(prev);
        }
void getBitWidth_io_cmd_ready(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_cmd_ready(result);
           svSetScope(prev);
        }
void getBits_io_cmd_ready(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_cmd_ready(result);
           svSetScope(prev);
        }
void getBitWidth_io_cmd_valid(int* result) {
           svScope prev = setScopeToTestBench();
           getBitWidthImpl_io_cmd_valid(result);
           svSetScope(prev);
        }
void getBits_io_cmd_valid(svBitVecVal* result) {
           svScope prev = setScopeToTestBench();
           getBitsImpl_io_cmd_valid(result);
           svSetScope(prev);
        }
void setBits_io_cmd_valid(const svBitVecVal* data) {
           svScope prev = setScopeToTestBench();
           setBitsImpl_io_cmd_valid(data);
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
    case 2: // io_status_cOccupancy
      getBitWidth_io_status_cOccupancy(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_status_cOccupancy;
      return 0;
    case 3: // io_status_bOccupancy
      getBitWidth_io_status_bOccupancy(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_status_bOccupancy;
      return 0;
    case 4: // io_status_aOccupancy
      getBitWidth_io_status_aOccupancy(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_status_aOccupancy;
      return 0;
    case 5: // io_status_progress
      getBitWidth_io_status_progress(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_status_progress;
      return 0;
    case 6: // io_status_err
      getBitWidth_io_status_err(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_status_err;
      return 0;
    case 7: // io_status_done
      getBitWidth_io_status_done(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_status_done;
      return 0;
    case 8: // io_status_busy
      getBitWidth_io_status_busy(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_status_busy;
      return 0;
    case 9: // io_dma2Ext_cmd_payload_addr
      getBitWidth_io_dma2Ext_cmd_payload_addr(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma2Ext_cmd_payload_addr;
      return 0;
    case 10: // io_dma2Ext_cmd_ready
      getBitWidth_io_dma2Ext_cmd_ready(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma2Ext_cmd_ready;
      return 0;
    case 11: // io_dma2Ext_cmd_valid
      getBitWidth_io_dma2Ext_cmd_valid(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma2Ext_cmd_valid;
      return 0;
    case 12: // io_dma2Ext_rsp_payload_rsp
      getBitWidth_io_dma2Ext_rsp_payload_rsp(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma2Ext_rsp_payload_rsp;
      return 0;
    case 13: // io_dma2Ext_rsp_payload_data
      getBitWidth_io_dma2Ext_rsp_payload_data(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma2Ext_rsp_payload_data;
      return 0;
    case 14: // io_dma2Ext_rsp_ready
      getBitWidth_io_dma2Ext_rsp_ready(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma2Ext_rsp_ready;
      return 0;
    case 15: // io_dma2Ext_rsp_valid
      getBitWidth_io_dma2Ext_rsp_valid(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma2Ext_rsp_valid;
      return 0;
    case 16: // io_dma1Ext_isWrite
      getBitWidth_io_dma1Ext_isWrite(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma1Ext_isWrite;
      return 0;
    case 17: // io_dma1Ext_grant
      getBitWidth_io_dma1Ext_grant(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma1Ext_grant;
      return 0;
    case 18: // io_dma1Ext_req
      getBitWidth_io_dma1Ext_req(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma1Ext_req;
      return 0;
    case 19: // io_dma1Ext_len
      getBitWidth_io_dma1Ext_len(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma1Ext_len;
      return 0;
    case 20: // io_dma1Ext_addr
      getBitWidth_io_dma1Ext_addr(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma1Ext_addr;
      return 0;
    case 21: // io_dma1Ext_writeData_payload
      getBitWidth_io_dma1Ext_writeData_payload(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma1Ext_writeData_payload;
      return 0;
    case 22: // io_dma1Ext_writeData_ready
      getBitWidth_io_dma1Ext_writeData_ready(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma1Ext_writeData_ready;
      return 0;
    case 23: // io_dma1Ext_writeData_valid
      getBitWidth_io_dma1Ext_writeData_valid(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma1Ext_writeData_valid;
      return 0;
    case 24: // io_dma1Ext_readData_payload
      getBitWidth_io_dma1Ext_readData_payload(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma1Ext_readData_payload;
      return 0;
    case 25: // io_dma1Ext_readData_ready
      getBitWidth_io_dma1Ext_readData_ready(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma1Ext_readData_ready;
      return 0;
    case 26: // io_dma1Ext_readData_valid
      getBitWidth_io_dma1Ext_readData_valid(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma1Ext_readData_valid;
      return 0;
    case 27: // io_dma0Ext_isWrite
      getBitWidth_io_dma0Ext_isWrite(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma0Ext_isWrite;
      return 0;
    case 28: // io_dma0Ext_grant
      getBitWidth_io_dma0Ext_grant(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma0Ext_grant;
      return 0;
    case 29: // io_dma0Ext_req
      getBitWidth_io_dma0Ext_req(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma0Ext_req;
      return 0;
    case 30: // io_dma0Ext_len
      getBitWidth_io_dma0Ext_len(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma0Ext_len;
      return 0;
    case 31: // io_dma0Ext_addr
      getBitWidth_io_dma0Ext_addr(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma0Ext_addr;
      return 0;
    case 32: // io_dma0Ext_writeData_payload
      getBitWidth_io_dma0Ext_writeData_payload(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma0Ext_writeData_payload;
      return 0;
    case 33: // io_dma0Ext_writeData_ready
      getBitWidth_io_dma0Ext_writeData_ready(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma0Ext_writeData_ready;
      return 0;
    case 34: // io_dma0Ext_writeData_valid
      getBitWidth_io_dma0Ext_writeData_valid(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma0Ext_writeData_valid;
      return 0;
    case 35: // io_dma0Ext_readData_payload
      getBitWidth_io_dma0Ext_readData_payload(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma0Ext_readData_payload;
      return 0;
    case 36: // io_dma0Ext_readData_ready
      getBitWidth_io_dma0Ext_readData_ready(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma0Ext_readData_ready;
      return 0;
    case 37: // io_dma0Ext_readData_valid
      getBitWidth_io_dma0Ext_readData_valid(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_dma0Ext_readData_valid;
      return 0;
    case 38: // io_resp_payload_err
      getBitWidth_io_resp_payload_err(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_resp_payload_err;
      return 0;
    case 39: // io_resp_payload_done
      getBitWidth_io_resp_payload_done(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_resp_payload_done;
      return 0;
    case 40: // io_resp_payload_data
      getBitWidth_io_resp_payload_data(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_resp_payload_data;
      return 0;
    case 41: // io_resp_ready
      getBitWidth_io_resp_ready(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_resp_ready;
      return 0;
    case 42: // io_resp_valid
      getBitWidth_io_resp_valid(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_resp_valid;
      return 0;
    case 43: // io_cmd_payload_data1
      getBitWidth_io_cmd_payload_data1(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_cmd_payload_data1;
      return 0;
    case 44: // io_cmd_payload_data0
      getBitWidth_io_cmd_payload_data0(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_cmd_payload_data0;
      return 0;
    case 45: // io_cmd_payload_reg1Addr
      getBitWidth_io_cmd_payload_reg1Addr(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_cmd_payload_reg1Addr;
      return 0;
    case 46: // io_cmd_payload_reg0Addr
      getBitWidth_io_cmd_payload_reg0Addr(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_cmd_payload_reg0Addr;
      return 0;
    case 47: // io_cmd_payload_op
      getBitWidth_io_cmd_payload_op(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_cmd_payload_op;
      return 0;
    case 48: // io_cmd_ready
      getBitWidth_io_cmd_ready(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_cmd_ready;
      return 0;
    case 49: // io_cmd_valid
      getBitWidth_io_cmd_valid(bitWidth);
      *getter = (void(*)(uint8_t*))getBits_io_cmd_valid;
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
    case 10: // io_dma2Ext_cmd_ready
      getBitWidth_io_dma2Ext_cmd_ready(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_dma2Ext_cmd_ready;
      return 0;
    case 12: // io_dma2Ext_rsp_payload_rsp
      getBitWidth_io_dma2Ext_rsp_payload_rsp(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_dma2Ext_rsp_payload_rsp;
      return 0;
    case 13: // io_dma2Ext_rsp_payload_data
      getBitWidth_io_dma2Ext_rsp_payload_data(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_dma2Ext_rsp_payload_data;
      return 0;
    case 15: // io_dma2Ext_rsp_valid
      getBitWidth_io_dma2Ext_rsp_valid(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_dma2Ext_rsp_valid;
      return 0;
    case 17: // io_dma1Ext_grant
      getBitWidth_io_dma1Ext_grant(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_dma1Ext_grant;
      return 0;
    case 22: // io_dma1Ext_writeData_ready
      getBitWidth_io_dma1Ext_writeData_ready(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_dma1Ext_writeData_ready;
      return 0;
    case 24: // io_dma1Ext_readData_payload
      getBitWidth_io_dma1Ext_readData_payload(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_dma1Ext_readData_payload;
      return 0;
    case 26: // io_dma1Ext_readData_valid
      getBitWidth_io_dma1Ext_readData_valid(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_dma1Ext_readData_valid;
      return 0;
    case 28: // io_dma0Ext_grant
      getBitWidth_io_dma0Ext_grant(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_dma0Ext_grant;
      return 0;
    case 33: // io_dma0Ext_writeData_ready
      getBitWidth_io_dma0Ext_writeData_ready(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_dma0Ext_writeData_ready;
      return 0;
    case 35: // io_dma0Ext_readData_payload
      getBitWidth_io_dma0Ext_readData_payload(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_dma0Ext_readData_payload;
      return 0;
    case 37: // io_dma0Ext_readData_valid
      getBitWidth_io_dma0Ext_readData_valid(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_dma0Ext_readData_valid;
      return 0;
    case 41: // io_resp_ready
      getBitWidth_io_resp_ready(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_resp_ready;
      return 0;
    case 43: // io_cmd_payload_data1
      getBitWidth_io_cmd_payload_data1(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_cmd_payload_data1;
      return 0;
    case 44: // io_cmd_payload_data0
      getBitWidth_io_cmd_payload_data0(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_cmd_payload_data0;
      return 0;
    case 45: // io_cmd_payload_reg1Addr
      getBitWidth_io_cmd_payload_reg1Addr(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_cmd_payload_reg1Addr;
      return 0;
    case 46: // io_cmd_payload_reg0Addr
      getBitWidth_io_cmd_payload_reg0Addr(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_cmd_payload_reg0Addr;
      return 0;
    case 47: // io_cmd_payload_op
      getBitWidth_io_cmd_payload_op(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_cmd_payload_op;
      return 0;
    case 49: // io_cmd_valid
      getBitWidth_io_cmd_valid(bitWidth);
      *setter = (void(*)(const uint8_t*))setBits_io_cmd_valid;
      return 0;
    default:
      return -1;
  }
}

} // extern "C"

