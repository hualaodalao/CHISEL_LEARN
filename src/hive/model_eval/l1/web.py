from __future__ import annotations

import json
from pathlib import Path
from typing import TYPE_CHECKING

from ..common.manifest import Manifest
from ..hw.profiles import HwProfile

if TYPE_CHECKING:
    from .timeline import L1Result


def report_payload(profile: HwProfile, results: list[L1Result], manifest: Manifest) -> dict:
    cases = []
    for result in results:
        case = result.to_dict(debug=True)
        case["label"] = f"{result.workload.label} / {result.attention}"
        cases.append(case)
    iface = profile.local_iface_bytes_per_s_per_core
    return {
        "manifest": manifest.to_dict(),
        "digest": manifest.digest,
        "hardware": {
            "profile": profile.name,
            "configured_cores": profile.n_core,
            "active_cores": len(results[0].topology.active_cores) if results else 0,
            "freq_hz": profile.freq_hz,
            "dram_gbps": profile.dram_peak_bytes_per_s / 1e9,
            "effective_dram_gbps": profile.dram_effective_bytes_per_s / 1e9,
            "sram_bytes_per_core": profile.sram_bytes_per_core,
            "local_iface_gbps": None if iface is None else iface / 1e9,
        },
        "cases": cases,
    }


def render_html(payload: dict) -> str:
    template = Path(__file__).with_name("resources.html").read_text(encoding="utf-8")
    encoded = json.dumps(payload, ensure_ascii=False, allow_nan=False, separators=(",", ":"))
    # HTML recognizes a script closing tag before the embedded JSON is decoded.
    for character, escaped in (("&", "\\u0026"), ("<", "\\u003c"), (">", "\\u003e"),
                               ("\u2028", "\\u2028"), ("\u2029", "\\u2029")):
        encoded = encoded.replace(character, escaped)
    return template.replace("__L1_PAYLOAD__", encoded)
