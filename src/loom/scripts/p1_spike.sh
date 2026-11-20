#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT"

OUT="generated/loom/formal"
mkdir -p "$OUT"

LOG="$OUT/p1_bmc.log"
yosys -p "read_verilog -formal -sv src/loom/formal/p1_token_4x4.sv; \
prep -top p1_token_4x4; \
async2sync; \
chformal -lower; \
sat -seq 12 -set-init-zero -prove-asserts" >"$LOG" 2>&1

if ! grep -q "SAT proof finished - no model found: SUCCESS" "$LOG"; then
  tail -40 "$LOG"
  echo "P1 FAILED: see $LOG"
  exit 1
fi

echo "P1 OK: 4x4 token model, no counterexample within 12 cycles (bounded RESP_TOTAL)"
