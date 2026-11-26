#!/usr/bin/env bash
# Area calibration on nangate45. Emits representative configs, hoists firtool
# local variables (yosys 0.71 cannot parse `automatic` inside always), then
# synthesizes and reports the top-module area.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
LIB="${NANGATE_LIB:-$HOME/.cache/pdk/nangate45/NangateOpenCellLibrary_typical.lib}"
OUT="${1:-$ROOT/generated/loom/calibration}"
mkdir -p "$OUT"
cd "$ROOT"

if [ ! -f "$LIB" ]; then
  echo "missing liberty: $LIB (set NANGATE_LIB)"
  exit 2
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

measure() {
  local name="$1" top="$2"
  local dir="$WORK/$name"
  sbt -J-Xmx6G -batch -Dsbt.supershell=false \
    "runMain loom.LoomMain emit --config src/loom/examples/$name.json --out $dir" >/dev/null

  python3 - "$dir" <<'PY'
import glob, re, sys
root = sys.argv[1]
pat = re.compile(r'^\s*automatic\s+logic\s*(\[[^\]]*\])?\s+(\w+)\s*=\s*(.*?);\s*(//.*)?$')
for f in glob.glob(root + '/*.sv'):
    lines = open(f).read().split('\n')
    decls, out = [], []
    for ln in lines:
        m = pat.match(ln)
        if m:
            rng = m.group(1) or ''
            decls.append(f'  wire {rng + " " if rng else ""}{m.group(2)} = {m.group(3)};')
        else:
            out.append(ln)
    txt = '\n'.join(out)
    if decls:
        i = txt.find('\n);')
        if i >= 0:
            txt = txt[:i + 3] + '\n' + '\n'.join(decls) + txt[i + 3:]
    txt = txt.replace("'{", "{")
    open(f, 'w').write(txt)
PY

  local rtl
  rtl="$(grep -v '^verification/' "$dir/filelist.f" | sed "s|^|$dir/|" | tr '\n' ' ')"
  yosys -p "read_verilog -sv $rtl; hierarchy -top $top; memory; synth -top $top; \
dfflibmap -liberty $LIB; abc -liberty $LIB; opt -fast; stat -liberty $LIB" 2>/dev/null \
    | grep -E "Chip area for top module" | tail -1
}

A2=$(measure minimal_2x2 LmTop | sed -E "s/.*: //")
A4=$(measure minimal_4x4 LmTop | sed -E "s/.*: //")
AB=$(measure minimal_4x4_banked LmTop | sed -E "s/.*: //")

python3 - "$OUT" "$A2" "$A4" "$AB" <<'PY'
import json, sys
out, a2, a4, ab = sys.argv[1], float(sys.argv[2]), float(sys.argv[3]), float(sys.argv[4])
cp2, cp4 = 4, 16
k = (a4 - a2) / (cp4 - cp2)
b = a2 - k * cp2
d = ab - a4
doc = {
    "schema": "loom/calibration/v1",
    "process": "nangate45 typical, yosys 0.71 synth + abc",
    "configs": {
        "minimal_2x2": {"crosspoints": cp2, "areaUm2": round(a2, 3)},
        "minimal_4x4": {"crosspoints": cp4, "areaUm2": round(a4, 3)},
        "minimal_4x4_banked": {"crosspoints": cp4, "areaUm2": round(ab, 3)}
    },
    "fit": {
        "areaPerCrosspointUm2": round(k, 3),
        "fixedUm2": round(b, 3),
        "bankedStageUm2": round(d, 3),
        "formula": "area = fixed + perCrosspoint*crosspoints + bankedStage*#bankedRouters"
    },
    "notes": [
        "areas include VC queues and NIs; per-crosspoint embeds buffer scaling",
        "no clock tree, SRAM macros or wiring; compare configs, not absolute silicon"
    ]
}
open(out + "/area.json", "w").write(json.dumps(doc, indent=2, sort_keys=True) + "\n")
print(f"2x2={a2:.1f} 4x4={a4:.1f} banked={ab:.1f} k={k:.1f} b={b:.1f} bankedDelta={d:.1f}")
PY
