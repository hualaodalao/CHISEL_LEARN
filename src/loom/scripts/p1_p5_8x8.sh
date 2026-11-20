#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT"

OUT="generated/loom/formal/token8x8"
mkdir -p "$OUT"

run_sat() {
  local top="$1" file="$2" depth="$3" log="$4" label="$5"
  yosys -p "read_verilog -formal -sv $file; \
prep -top $top; \
async2sync; \
chformal -lower; \
sat -seq $depth -set-init-zero -prove-asserts" >"$log" 2>&1
  if ! grep -q "SAT proof finished - no model found: SUCCESS" "$log"; then
    tail -40 "$log"
    echo "$label FAILED: see $log"
    exit 1
  fi
  echo "$label OK: bounded depth $depth"
}

# P1-P3 safety plus route safety on the 8x8 token model.
run_sat token_8x8 src/loom/formal/token_8x8.sv 12 \
  "$OUT/p1_p3_8x8.log" "P1-P3 + route (8x8 safety)"

# P5 bounded progress on the dedicated 8-way RR model.
run_sat token_rr8 src/loom/formal/token_rr8.sv 20 \
  "$OUT/p5_rr8.log" "P5 (8-way RR progress)"

echo "P1-P3 + route OK (depth 12), P5 OK (depth 20, bounded)"
