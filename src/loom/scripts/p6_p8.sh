#!/usr/bin/env bash
# P6/P7/P8 bounded proofs on the token models (yosys-sat).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT"

run() {
  local top="$1" file="$2" depth="$3" label="$4"
  local log="$(mktemp)"
  yosys -p "read_verilog -formal -sv $file; \
prep -top $top; \
async2sync; \
memory; \
chformal -lower; \
sat -seq $depth -set-init-zero -prove-asserts" >"$log" 2>&1
  if ! grep -q "SAT proof finished - no model found: SUCCESS" "$log"; then
    tail -40 "$log"
    rm -f "$log"
    echo "$label FAILED"
    exit 1
  fi
  rm -f "$log"
  echo "$label OK: bounded depth $depth"
}

run token_response src/loom/formal/token_response.sv 12 "P6/P8 (no loss, exact-once response)"
run token_cdc      src/loom/formal/token_cdc.sv      12 "P7 (CDC credit)"
run token_cdc_buf  src/loom/formal/token_cdc_buf.sv  12 "G9 (async boundary buffer)"

echo "P6/P7/P8/G9 OK"
