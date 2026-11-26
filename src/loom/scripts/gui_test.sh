#!/usr/bin/env bash
# Loom GUI pure-model unit tests (node:test, zero npm dependencies).
# Usage: ./src/loom/scripts/gui_test.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT"
exec node --test src/loom/gui/test/*.test.mjs