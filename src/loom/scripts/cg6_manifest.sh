#!/usr/bin/env bash
# CG6: verify every file listed in manifest.json still matches its SHA-256.
set -euo pipefail

DIR="${1:?usage: cg6_manifest.sh <emitted-dir>}"
python3 - "$DIR" <<'PY'
import hashlib, json, os, sys

d = sys.argv[1]
manifest = os.path.join(d, "manifest.json")
if not os.path.isfile(manifest):
    print(f"CG6 FAIL: no manifest in {d}")
    sys.exit(2)

with open(manifest) as f:
    m = json.load(f)

bad = []
for entry in m.get("files", []):
    path = os.path.join(d, entry["path"])
    if not os.path.isfile(path):
        bad.append(f"missing {entry['path']}")
        continue
    h = hashlib.sha256(open(path, "rb").read()).hexdigest()
    if h != entry["sha256"]:
        bad.append(f"modified {entry['path']}")

if bad:
    for b in bad:
        print(f"CG6 FAIL: {b}")
    sys.exit(1)

print(f"CG6 OK: {len(m.get('files', []))} artifacts match manifest")
PY
