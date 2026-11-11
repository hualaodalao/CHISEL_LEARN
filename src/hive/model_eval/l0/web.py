from __future__ import annotations

import json
from dataclasses import dataclass
from math import isclose, prod
from pathlib import Path

from ..common.manifest import Manifest
from ..common.workload import Phase
from ..frontend.layers.base import LAYERS, LayerInstance, layer_l0
from ..frontend.plan import ModelPlan
from ..frontend.spec import NumFormat
from ..hw.profiles import HwProfile
from .roofline import L0Result


@dataclass(frozen=True)
class _WeightTensor:
    name: str
    shape: tuple[int, ...]

    @property
    def elements(self) -> int:
        return prod(self.shape)


def _row(result: L0Result, profile: HwProfile, fmt: NumFormat, id: str, name: str,
         kind: str, mac: float, params: float, kv_bytes: float, state_bytes: float,
         tensors) -> dict:
    weights_bytes = params * result.bytes_per_param
    traffic = weights_bytes + kv_bytes + state_bytes
    mac_rate = profile.mac_per_second(fmt.mac_family)
    bw = profile.dram_effective_bytes_per_s
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


def _leaf_weights(layer: LayerInstance) -> tuple:
    definition = LAYERS.get(layer.type, layer.version)
    if definition.children is None:
        return tuple(_WeightTensor(weight.role, weight.shape) for weight in layer.weights)
    weights = ()
    for child in definition.children(layer):
        weights += _leaf_weights(child)
    return weights


def _unique_params(layer: LayerInstance) -> int:
    definition = LAYERS.get(layer.type, layer.version)
    if definition.children is not None:
        return sum(_unique_params(child) for child in definition.children(layer))
    return sum(prod(weight.shape) for weight in layer.weights)


def _plan_layer_resources(model: ModelPlan, profile: HwProfile, fmt: NumFormat,
                          result: L0Result) -> list[dict]:
    point = result.workload
    S, batch = point.seq_len, point.batch
    rows = []
    for layer in model.layers:
        l0 = layer_l0(layer)
        if not (l0.mac_fixed or l0.mac_s_coef or l0.mac_s2_coef or l0.mac_output
                or l0.params_streamed_per_token or l0.kv_bytes_per_token or l0.state_bytes):
            continue
        if point.phase is Phase.DECODE:
            mac = (l0.mac_fixed + l0.mac_s_coef * S + l0.mac_output) * batch
            params = l0.params_streamed_per_token
        elif point.phase is Phase.PREFILL:
            mac = (l0.mac_fixed * S + l0.mac_s2_coef * S * S + l0.mac_output) * batch
            params = _unique_params(layer)
        else:
            mac = (l0.mac_fixed + l0.mac_output) * batch
            params = l0.params_streamed_per_token
        kv_bytes = 0 if point.phase is Phase.FRAME else l0.kv_bytes_per_token * S * batch
        definition = LAYERS.get(layer.type, layer.version)
        kind = ("block" if definition.children is not None
                else "head" if l0.mac_output else
                "norm" if layer.type == "generic.rms_norm" else "layer")
        rows.append(_row(result, profile, fmt, layer.id, layer.id, kind, mac, params,
                         kv_bytes, l0.state_bytes * batch, _leaf_weights(layer)))

    for key, expected in (("mac", result.mac), ("traffic_bytes", result.traffic_bytes)):
        actual = sum(r[key] for r in rows)
        if not isclose(actual, expected, rel_tol=1e-12, abs_tol=1e-6):
            raise ValueError(f"逐层 {key} 合计 {actual} 与 L0 总账 {expected} 不一致")
    return rows


def layer_resources(
    model: ModelPlan, profile: HwProfile, fmt: NumFormat, result: L0Result
) -> list[dict]:
    return _plan_layer_resources(model, profile, fmt, result)


def report_payload(
    model: ModelPlan, profile: HwProfile, fmt: NumFormat,
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
