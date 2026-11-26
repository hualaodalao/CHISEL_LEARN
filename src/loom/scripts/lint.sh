#!/usr/bin/env bash
# Elaboration/CDC lint (CHI caliber, S6/MIG-12): Verilator parses every emitted
# representative top and its TB skeleton. Warnings are tolerated; errors fail
# the gate. Inputs are the loom/v2 user examples; tops are the CHI generation
# layer (ChiNetworkTop / ChiWrapperTop / ChiPartitionTop).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

lint() {
  local dir="$1" top="$2"; shift 2
  local extra=("$@")
  local more=()
  if [ ${#extra[@]} -gt 0 ]; then more=("${extra[@]}"); fi
  ( cd "$dir" && verilator --lint-only -Wno-fatal --top-module "${top}_tb" \
    -f filelist.f "${more[@]:-}" "tb/${top}_tb.sv" >/dev/null 2>"$dir/lint.err" ) || {
      echo "LINT FAILED: $top in $dir"
      tail -20 "$dir/lint.err"
      exit 1
    }
  echo "lint OK: $top"
}

emit() {
  local name="$1"
  sbt -J-Xmx6G -batch -Dsbt.supershell=false \
    "runMain loom.LoomMain emit --config src/loom/examples/$name.json --out $WORK/$name" >/dev/null
}

emit minimal_4x4
lint "$WORK/minimal_4x4" ChiNetworkTop
if [ -d "$WORK/minimal_4x4/sram" ]; then
  ( cd "$WORK/minimal_4x4/sram" && verilator --lint-only -Wno-fatal -f filelist.f >/dev/null 2>&1 ) \
    || { echo "LINT FAILED: SRAM blocks"; exit 1; }
  echo "lint OK: SRAM blocks"
fi

emit minimal_8x8
lint "$WORK/minimal_8x8" ChiNetworkTop

emit mesh_2x2_xy
lint "$WORK/mesh_2x2_xy" ChiNetworkTop

emit ring_4
lint "$WORK/ring_4" ChiNetworkTop

emit chain_split
lint "$WORK/chain_split" ChiNetworkTop
( cd "$WORK/chain_split/tops" && verilator --lint-only -Wno-fatal --top-module ChiWrapperTop_tb \
  -f filelist.f "$WORK/chain_split/tb/ChiWrapperTop_tb.sv" >/dev/null 2>"$WORK/chain_split/tops/lint.err" ) || {
    echo "LINT FAILED: ChiWrapperTop"; tail -20 "$WORK/chain_split/tops/lint.err"; exit 1; }
echo "lint OK: ChiWrapperTop"
for part in front back; do
  ( cd "$WORK/chain_split/partitions/$part" && verilator --lint-only -Wno-fatal \
    --top-module ChiPartitionTop -f filelist.f >/dev/null 2>&1 ) || {
      echo "LINT FAILED: partition $part"; exit 1; }
  echo "lint OK: partition $part"
done

# MIG-11 (S6): the legacy `blocks` emission (LmAsyncFifo/LmCdcCredit/
# LmWidthAdapter) is retired. The CDC path stays linted through ChiAsyncLink
# inside the async wrapper below.
emit chain_split_async
( cd "$WORK/chain_split_async/tops" && verilator --lint-only -Wno-fatal --top-module ChiWrapperTop_tb \
  -f filelist.f "$WORK/chain_split_async/tb/ChiWrapperTop_tb.sv" >/dev/null 2>"$WORK/chain_split_async/tops/lint.err" ) || {
    echo "LINT FAILED: ChiWrapperTop (async)"; tail -20 "$WORK/chain_split_async/tops/lint.err"; exit 1; }
echo "lint OK: ChiWrapperTop (async)"