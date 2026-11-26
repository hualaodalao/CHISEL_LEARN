#!/usr/bin/env bash
# CG2 (GEN-07): the same normalized loom/v2 IR must emit byte-identical CHI
# artifacts in two independent runs. Default input is a v2 user example.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
CFG="${1:-$ROOT/src/loom/examples/minimal_4x4.json}"
A="$(mktemp -d)"
B="$(mktemp -d)"
cleanup() { rm -rf "$A" "$B"; }
trap cleanup EXIT

cd "$ROOT"

emit() {
  sbt -J-Xmx6G -batch -Dsbt.supershell=false \
    "runMain loom.LoomMain emit --config $CFG --out $1" >/dev/null
}

emit "$A"
emit "$B"

# filelist.f and the verification layer are generated too; all must match.
diff -r "$A" "$B"
echo "CG2 OK: deterministic artifacts for $CFG"
