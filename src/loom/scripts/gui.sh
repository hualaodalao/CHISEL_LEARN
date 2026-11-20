#!/usr/bin/env bash
# One-command GUI: emit a representative config and serve the editor.
# Usage: gui.sh [config.json] [out-dir]   (PORT=8000 by default)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
CFG="${1:-src/loom/examples/minimal_4x4.json}"
OUT="${2:-generated/loom/gui}"
PORT="${PORT:-8000}"

cd "$ROOT"

echo "emitting $CFG -> $OUT"
sbt -J-Xmx6G -batch -Dsbt.supershell=false \
  "runMain loom.LoomMain emit --config $CFG --out $OUT" >/dev/null

echo "GUI: http://localhost:$PORT/   (Ctrl-C to stop)"
exec sbt -J-Xmx6G -Dsbt.supershell=false \
  "runMain loom.LoomMain serve --dir $OUT --port $PORT --examples src/loom/examples"
