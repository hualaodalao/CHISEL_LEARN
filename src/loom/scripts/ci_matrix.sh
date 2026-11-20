#!/usr/bin/env bash
# Loom CI matrix: static gates, emission, determinism, report baseline and
# artifact integrity. Pass --formal to include bounded proofs (nightly).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
FORMAL="${1:-}"
cd "$ROOT"

EXAMPLES=(minimal_2x2 minimal_4x4 minimal_4x4_banked minimal_8x8 tree_3r chain_4r chain_split chain_split_async mesh_2x2_xy)

run() {
  sbt -J-Xmx6G -batch -Dsbt.supershell=false "$@" >/dev/null
}

echo "[CG0] static gates for ${#EXAMPLES[@]} configurations"
for e in "${EXAMPLES[@]}"; do
  run "runMain loom.LoomMain check --config src/loom/examples/$e.json --out /tmp/loom-ci/$e"
done

echo "[CG2] deterministic emission for representatives"
for e in minimal_4x4 tree_3r chain_split chain_split_async; do
  ./src/loom/scripts/cg2_determinism.sh "src/loom/examples/$e.json" | tail -1
done

echo "[CG5] report baseline diff"
A="$(mktemp -d)"
B="$(mktemp -d)"
trap 'rm -rf "$A" "$B"' EXIT
run "runMain loom.LoomMain emit --config src/loom/examples/chain_split.json --out $A"
run "runMain loom.LoomMain emit --config src/loom/examples/chain_split.json --out $B"
run "runMain loom.LoomMain diff --a $A --b $B"

echo "[CG6] manifest integrity"
./src/loom/scripts/cg6_manifest.sh "$A" | tail -1

echo "[CG7] elaborated lint"
./src/loom/scripts/lint.sh >/dev/null
echo "lint OK"

if [ "$FORMAL" = "--formal" ]; then
  echo "[CG4] bounded formal proofs"
  run "runMain loom.LoomMain formal --config src/loom/examples/chain_split.json --out $A"
fi

echo "CI matrix OK"
