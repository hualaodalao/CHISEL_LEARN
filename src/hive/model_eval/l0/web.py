from __future__ import annotations

import json
from math import isclose
from pathlib import Path

from ..common.manifest import Manifest
from ..common.workload import Phase
from ..frontend.spec import ModelSpec, NumFormat, TensorSpec
from ..hw.profiles import HwProfile
from .roofline import L0Result


def layer_resources(
    model: ModelSpec, profile: HwProfile, fmt: NumFormat, result: L0Result
) -> list[dict]:
    point = result.workload
    S, batch = point.seq_len, point.batch
    rows = []
    mac_rate = profile.mac_per_second(fmt.mac_family)
    bw = profile.dram_effective_bytes_per_s

    def row(id, name, kind, mac, params, kv_bytes, state_bytes, tensors):
        weights_bytes = params * result.bytes_per_param
        traffic = weights_bytes + kv_bytes + state_bytes
        t_bw, t_mac = traffic / bw, mac / mac_rate
        return {
            "id": id,
            "name": name,
            "kind": kind,
            "mac": mac,
            "weights_bytes": weights_bytes,
            "kv_bytes": kv_bytes,
            "state_bytes": state_bytes,
            "traffic_bytes": traffic,
            "t_bandwidth_s": t_bw,
            "t_compute_s": t_mac,
            "t_lower_bound_s": max(t_bw, t_mac),
            "tensors": [
                {"name": t.name, "shape": list(t.shape), "elements": t.elements}
                for t in tensors
            ],
        }

    for layer_type in model.layer_types:
        if point.phase is Phase.DECODE:
            mac = (layer_type.mac_fixed + layer_type.mac_s_coef * S) * batch
            params = layer_type.params_streamed_per_token
        elif point.phase is Phase.PREFILL:
            mac = (layer_type.mac_fixed * S + layer_type.mac_s2_coef * S * S) * batch
            params = layer_type.params_unique
        else:
            mac = layer_type.mac_fixed * batch
            params = layer_type.params_streamed_per_token
        kv_bytes = 0 if point.phase is Phase.FRAME else layer_type.kv_bytes_per_token * S * batch
        for _ in range(layer_type.count):
            index = len(rows)
            rows.append(row(
                f"block-{index}", f"Block {index + 1:02d}", "block", mac, params,
                kv_bytes, layer_type.state_bytes * batch, layer_type.tensors,
            ))

    norm_mac = model.final_norm_mac * batch
    if point.phase is Phase.PREFILL:
        norm_mac *= S
    rows.append(row(
        "final-norm", "Final norm", "norm", norm_mac, model.hidden_size, 0, 0,
        (TensorSpec("final_norm", (model.hidden_size,)),),
    ))
    rows.append(row(
        "lm-head", "LM head", "head", model.lm_head_mac * batch,
        model.lm_head_params, 0, 0,
        (TensorSpec("lm_head", (model.vocab_size, model.hidden_size)),),
    ))

    for key, expected in (("mac", result.mac), ("traffic_bytes", result.traffic_bytes)):
        actual = sum(r[key] for r in rows)
        if not isclose(actual, expected, rel_tol=1e-12, abs_tol=1e-6):
            raise ValueError(f"逐层 {key} 合计 {actual} 与 L0 总账 {expected} 不一致")
    return rows


def report_payload(
    model: ModelSpec, profile: HwProfile, fmt: NumFormat,
    results: list[L0Result], manifest: Manifest,
) -> dict:
    cases = []
    for result in results:
        cases.append({
            "label": result.workload.label,
            "phase": result.workload.phase.value,
            "seq_len": result.workload.seq_len,
            "batch": result.workload.batch,
            "mac": result.mac,
            "traffic_bytes": result.traffic_bytes,
            "t_bandwidth_s": result.t_bandwidth_s,
            "t_compute_s": result.t_compute_s,
            "t_s": result.t_s,
            "throughput": result.throughput,
            "throughput_unit": result.throughput_unit,
            "bottleneck": result.bottleneck,
            "layers": layer_resources(model, profile, fmt, result),
        })
    iface = profile.local_iface_bytes_per_s_per_core
    return {
        "manifest": manifest.to_dict(),
        "digest": manifest.digest,
        "hardware": {
            "dram_gbps": profile.dram_peak_bytes_per_s / 1e9,
            "effective_dram_gbps": profile.dram_effective_bytes_per_s / 1e9,
            "mac_tmac_s": profile.mac_per_second(fmt.mac_family) / 1e12,
            "sram_bytes_per_core": profile.sram_bytes_per_core,
            "local_iface_gbps": None if iface is None else iface / 1e9,
        },
        "cases": cases,
    }


def render_html(payload: dict) -> str:
    template = Path(__file__).with_name("resources.html").read_text(encoding="utf-8")
    encoded = json.dumps(payload, ensure_ascii=False, allow_nan=False, separators=(",", ":"))
    # JSON lives in a script element; HTML parses its closing tag before JSON is decoded.
    for character, escaped in (("&", "\\u0026"), ("<", "\\u003c"), (">", "\\u003e"),
                               ("\u2028", "\\u2028"), ("\u2029", "\\u2029")):
        encoded = encoded.replace(character, escaped)
    return template.replace("__L0_PAYLOAD__", encoded)
